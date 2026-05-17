
# 总体总结 
**在 `businessLine` 硬隔离边界内，按 `shopId` / 店铺客服小组分流人类客服**（**mall-chat：`tenantId` 不参与选人、不参与分配 SQL**；可来自店铺写入会话，仅作元数据）。同时把 AI 的作用定位为：**标准问题处理、知识库检索、翻译、摘要、建议回复、降低人工记忆压力、提高单个客服承载量**。AI 主要分担重复咨询、基础商品问答和人工客服记忆压力，并不是完全替代客服。

---

# AI 客服与店铺客服小组方案

## 核心结论

当前阶段，客服体系采用：

> **公司统一雇佣客服；`businessLine` 表示业务域隔离边界，`shopId` 表示具体店铺分流入口。人类客服进线在 `businessLine` + `shopId`（人↔店关系，如 `chat_agent_shop_relation`）确定的店铺小组内再选具体人，**不以 `tenantId` 为选人池或路由条件**。`tenantId` 表示合伙人经营主体，可从店铺解析并落库到会话等，供归属、计费、运营视图使用；AI 作为群聊助手和客服外挂大脑，宜在 **`tenantId` + `businessLine` + `shopId`** 限定范围内做知识检索与辅助输出，不承担复杂售前、售后、税务、法律分诊主责。**

一句话：

```text
客服归公司，
业务线先隔离，
服务按店铺，
AI 做提效，
复杂判断由人处理。
```

AI 的价值不是让公司不需要客服，而是：

```text
减少重复咨询
降低客服记忆压力
提高单个客服承载量
让客服人数不随用户量和品类数量线性增长
```

---

## 一、总体原则

客服不是由运营负责人私人雇佣，而是由公司统一管理。

公司掌握：

```text
客服账号
客服权限
聊天记录
客户数据
客服系统
服务标准
```

但客服可以被绑定到某条 `businessLine` 下、与具体 `shopId` 关联的店铺小组长期服务（**选人按店 + 业务线，不按 tenant 划池**）。

这样既能保证公司对客户和数据的控制权，又能让客服熟悉具体店铺的商品、售后问题、用户习惯和运营策略。

---

## 二、tenantId 的定位

`tenantId` 表示：

> **合伙人经营主体 / 类卖家主体 / 独立运营单元。**

它不是简单等于某个自然人账号，而是一个经营单元。

例如：

```text
tenant A：日用品合伙经营主体
tenant B：工业品合伙经营主体
tenant C：宠物用品合伙经营主体
```

一个 tenant 可以包含：

```text
多个店铺
多个商品
多个运营人员
多个客服
自己的经营数据
自己的服务关系
```

运营负责人是 tenant 下的 owner / operator 角色。

如果只是负责人离职，优先做：

```text
tenantId 不变
shopId 不变
更换负责人
客服团队可以调整
历史订单、售后、聊天记录保持连续
```

只有当经营主体本身发生变化时，才考虑更换 tenant。

### mall-chat 与人类客服选人（实现约定）

在 **mall-chat** 内：**人类客服的分配与负载 SQL 不以 `tenantId` 为条件**。

**有 `shopId` 的店铺售前进线（主路径）**

```text
businessLine + shopId
  → 仅在 chat_agent_shop_relation 绑定的店铺小组内选人（通常为 pre-sales）
  → 仍分配给该组的售前客服（按负载等规则），不因「当前 WebSocket 不在线」改派到公司级
  → 由群聊机器人按**工作时间配置**向客户提示「客服忙碌」（工时内）或「非工作时间」（工时外，见下节），不改为 corporate 接店铺售前
```

**`agent_type = corporate`（公司级池）**

仅用于**无店铺入口**或**法务、税务、平台规则等**非店铺售前场景，**不承担**「某店售前不在线、池满、暂时无人可接」时的替补。

**无 `shopId` 但有 `businessLine`**

可走该业务线的兜底坐席配置（运营定义），**不等于**把店铺售前空缺自动转给 corporate。

会话上的 **`tenant_id` 可来自 `mall_shop.tenant_id`**（有店且店铺有值则写入，否则可为空），**仅作元数据**，与进线选人分离。

下文仍从**治理与 AI 知识边界**说明 `tenantId` 的含义；凡描述**人类进线 / 派单**，已与上表一致：**不再把「在 tenant 范围内找人」写成技术实现步骤**。

### mall-chat：机器人「客服忙碌 / 非工作时间」提示（产品约定与代码对照）

**产品约定**

- 客户从店铺进线后，会话仍绑定**店铺售前小组**中的客服（主接待 + 群成员）；售前暂时不在线或忙碌时，**不**用公司级（corporate）顶替接店铺咨询。
- **群聊机器人**（如成员 `666666` / `robot_agent`）在客户发消息、会话分配流程后，按**是否在工作时间内**发系统提示（**不**根据店铺售前 WebSocket 是否在线分支）：
  - **工作时间内**（默认 09:00～18:00，可配置 `chat.working-hours.*`）：`您好，客服正在忙碌中，请稍等片刻，我们会尽快为您服务！`
  - **非工作时间**：`您好，当前不是客服工作时间，但您发的消息我们已经记录，上班后及时反馈给您，感谢您的理解！`
- 同一 `conversation_id` 在**时间窗口内**（默认 4 小时，可配置 `chat.robot-auto-reply.window-hours`）只提示一次（Redis `chat:robot:auto-reply:{conversationId}`）。

**代码位置（便于排查）**

| 环节 | 类 / 方法 | 说明 |
|------|-----------|------|
| 触发入口 | `CustomerChatService#createAndAssignNewConversation` | 新会话分配后调用 `robotAgentService.sendAutoReplyMessage` |
| 触发入口 | `CustomerChatService#activateExistingConversation` | 老会话客户再发消息时同样调用 |
| 提示实现 | `RobotAgentService#sendAutoReplyMessage` | 按 `chat.working-hours.*` 选文案；`chat.robot-auto-reply.window-hours` 控制去重 |
| 消息落库与推送 | `RobotAgentService#sendRobotMessage` | 写入 `chat_message`，`senderId=robot_001`，推送给客户 |

**与代码对照（截至当前仓库）**

| 项 | 状态 | 说明 |
|----|------|------|
| 机器人忙碌 / 非工时文案 | **已实现** | `RobotAgentService#sendAutoReplyMessage`：按 `chat.working-hours` 二选一文案 + Redis 时间窗口去重；**不要求**先判断店铺售前是否在线。 |
| `RouteResult.hasAssignedHumanAgent` | **已按设计** | 表示是否成功分配到人类（`agentId != null`），**不**表示 WebSocket 在线，**不**驱动机器人文案。 |
| 有店不回退 corporate | **已修复** | `resolveShopPreSalesAgent` 仅在店铺售前池选人；池空则 `agentId=null`，**不会**回退 `corporate`。 |

**结论（mall-chat 人类进线 + 机器人）**：**店售前池内选人（含离线）+ 有店不回退 corporate + 机器人按工时发忙碌/非工时提示** 已与当前产品约定一致。文档 **第十八节阶段 2** 中的运营兜底、无店业务线兜底等，属后续规划，**非**本期机器人或进线必选项。

---

## 三、业务线隔离原则

客服虽然按 `shopId` 做主要分流，但业务线必须作为硬隔离边界。

也就是说：

> **不同 businessLine 的客服不能混用。**

例如：

```text
日常百货业务线的客服
不能接工业品业务线的咨询

工业品业务线的客服
不能接服饰、美妆、玩具等业务线的咨询
```

因为不同业务线的商品复杂度、售前知识、售后风险、用户问题完全不同。

```text
tenantId       = 合伙人经营主体 / 类卖家主体
businessLine   = 业务域隔离边界
shopId         = 店铺入口 / 店铺服务小组分流依据
agentId        = 具体客服
```

所以客服分配不能只看 `shopId`，还要先确认该店铺所属的 `businessLine`。

---

## 四、业务线、tenant、shop 的关系

一个 tenant 可以经营多个店铺，也可以覆盖一个或多个业务线。

但客服分配时必须保证：

```text
同一个 tenant 下，
不同 businessLine 的客服池仍然要隔离。
```

例如：

```text
tenant A：某合伙人经营主体
  ├─ shop 1：日常百货业务线
  ├─ shop 2：日常百货业务线
  └─ shop 3：工业品业务线
```

那么分配时不能因为都属于 `tenant A`，就把 `shop 3` 的工业品咨询分给日常百货客服。

正确分配是：

```text
shop 1 / shop 2
  → tenant A
  → 日常百货 businessLine
  → 日常百货店铺客服小组

shop 3
  → tenant A
  → 工业品 businessLine
  → 工业品店铺客服小组
```

也就是说：

> **`businessLine` 解决业务能力隔离，`shopId` 解决具体店铺分流；`tenantId` 解决经营主体归属（治理、计费、会话元数据）。人类客服选人**以 `businessLine` + `shopId`（店铺小组）为界**，**不以 `tenantId` 为池边界**；避免「同属 tenant 却混接不同业务线」应靠 **业务线隔离 + 正确的店↔人绑定**，而不是在分配 SQL 里按 tenant 筛人。**

---

## 五、更新后的客服分配主流程

### 5.1 有 shopId 的会话

有 `shopId` 的会话：**选人边界上的 `businessLine` 以网关透传为准**（`X-Business-Line`），**不是**仅凭 `shopId` 查店后反推业务线。须用 `shopId` 查 `mall_shop` 与网关业务线**核对**；不一致打 error 日志（后续接告警）。`tenantId` 可从店铺写入会话（元数据，不参与选人）。

```text
客户发送消息
  ↓
网关 / WebSocket 透传 businessLine（X-Business-Line，必选；无业务线不落库、不分配，见 5.3）
  ↓
消息携带 shopId
  ↓
按 shopId 查询 mall_shop（校验店铺存在；tenantId 等元数据可写入会话）
  ↓
核对业务线：网关 businessLine 与 mall_shop.business_line 须一致
       → 一致：以【网关 businessLine】为选人边界（chat_agent_shop_relation 等均带该 business_line）
       → 不一致：打 error 日志 [SHOP_BUSINESS_LINE_MISMATCH]（MallShopService#assertShopBusinessLineMatchesGateway，[TODO: alerting]）；当前实现不阻断链路，避免误配导致全量不可用
       → 店铺不存在：打 error 日志 [SHOP_ROUTING]（同上，后续告警）
  ↓
在该 businessLine + shopId 绑定的「店铺售前小组」内选人（负载等；可含当前离线售前）
  ↓
写入主接待客服 + 群成员（不因离线改派 corporate）
  ↓
机器人按工作时间配置提示「客服忙碌」或「非工作时间」（同会话时间窗口内去重）
  ↓
机器人（666666）常驻群聊辅助
```

核心逻辑（人类进线）：

```text
网关 businessLine（必选、选人边界）
  + shopId（须与 mall_shop 核对一致）
  ↓
店铺客服小组（chat_agent_shop_relation，同 business_line + shop_id）
  ↓
具体客服
```

不能变成：

```text
shopId
  ↓
按 tenantId 在库里筛客服
```

也不能变成：

```text
shopId
  ↓
全公司 / 全业务线客服混派
```

---

### 5.2 没有 shopId，但有 businessLine

如果用户不是从具体店铺进来，但请求里能确定 `businessLine`，则进入该业务线的兜底组。

```text
无 shopId
但有 businessLine
  ↓
进入该 businessLine 下的**业务线兜底组**（主管 / 默认负责人等；**非**店铺售前 vacant 时的 corporate，**不按 tenantId 划池**）
```

例如：

```text
用户从日常百货业务线活动页进入
但没有具体 shopId
  ↓
进入日常百货业务线兜底客服

用户从工业品业务线频道页进入
但没有具体 shopId
  ↓
进入工业品业务线兜底客服
```

这样仍然不会跨业务线。

---

### 5.3 `businessLine` 为进线必选（mall-chat 实现约定）

**不存在「无 `businessLine` 仍正常进线、落库、分配」的路径。** 网关 / WebSocket / HTTP 须透传 `X-Business-Line`；客户端握手或请求若拿不到业务线，**不应**进入 mall-chat 的正常会话与消息落库流程。

```text
无 businessLine（或未解析出有效业务线）
  ↓
不创建/不推进会话分配（handleConversation 直接跳过）
  ↓
chat_message / chat_conversation / chat_conversation_member 等表要求 business_line 必填，无业务线则不落库
```

账号、支付、税务、法律等平台类问题，仍须在**某一业务线上下文**下进线（例如用户从该业务线入口进入、或法务/平台频道带 `businessLine`），再走 **5.2 无 `shopId` 但有 `businessLine`**（如 `corporate` 池），**不是**「既没有店也没有业务线」的第三条路由。

---

## 六、店铺售前不在线 / 未配置绑定时，也不能跨业务线或公司级乱派

### 6.1 售前不在线或忙碌（仍属该店小组）

有 `shopId` 的咨询，**默认仍分配给该店绑定的售前客服**（按小组内规则选人），**不**因暂时无人挂 WebSocket 就改派 `corporate` 或其它业务线售前。

客户侧体验由**机器人**补充：

```text
客户发消息
  ↓
会话仍绑定店铺售前（主接待 + member）
  ↓
机器人按是否在工作时间内提示「客服忙碌」或「非工作时间」（时间窗口内去重）
  ↓
售前上线后继续在同一群内接待
```

**禁止**：店铺售前全员离线 → 自动改派 `corporate` 接店铺商品/订单类售前咨询。

### 6.2 店铺未配置任何绑定客服（运营缺口）

若 `chat_agent_shop_relation` 中**根本没有**绑定任何人，属于配置缺失，应在**同 `businessLine` 内**由运营兜底（主管、业务线默认负责人等），**仍不是**用 `corporate` 顶替日常店铺售前；`corporate` 留给法务税务等平台类入口（见第二节 mall-chat 约定）。

不能：

```text
工业品店铺咨询
  → 分给日常百货售前（跨 businessLine）

某店售前离线
  → 分给 corporate 接店铺售前（错误）
```

业务线隔离优先级高于“尽快找个人接”；**店铺售前与公司级职责分离**优先级高于“池空就 corporate”。

---

## 七、AI 也要遵守业务线隔离

> **说明**：本节约束 **AI 知识检索与辅助输出** 的范围。人类客服进线选人见第二节「mall-chat 与人类客服选人」：**不按 `tenantId` 分配**。

AI 虽然是群聊助手，但它检索知识库时也要遵守业务线边界。

```text
日常百货会话
  → 只能检索日常百货相关商品、FAQ、售后规则

工业品会话
  → 只能检索工业品相关商品、技术参数、FAQ、售后规则
```

不能让 AI 在日常百货会话里引用工业品知识，也不能让工业品客服看到不属于自己业务线的会话和资料。

AI 的知识检索范围应该由：

```text
tenantId
businessLine
shopId
productId
```

共同限制。

这样 AI 才不会因为知识库混用导致回答错误。

---

## 八、更新后的最终一句话

最终方案应该改成：

> **客服由公司统一雇佣和建号；`businessLine` 为进线必选（无业务线不落库、不分配）。`shopId` 表示具体店铺分流入口。有 `shopId` 的会话在 `businessLine` + 店铺绑定池内选售前等人；售前离线时仍归该组，由机器人提示忙碌。`corporate` 仅承担法务税务等**无店但有业务线**的入口，**不**顶替店铺售前空缺。无 `shopId` 但有 `businessLine` 的会话进入该业务线兜底组（如 corporate）。**`tenantId` 可落库，不参与人类选人 SQL**。**AI** 宜在 **`tenantId` + `businessLine` + `shopId`** 范围内做检索与辅助。

人类进线以 **`businessLine` + `shopId`（人↔店关系）** 为主；`tenantId` 保留在治理、计费、会话元数据及 **AI 知识边界** 中，与 mall-chat 实现一致。

---

## 九、店铺是主要分流入口

在已确定 **`businessLine`** 的前提下（见上文第三至五节），`shopId` 仍是最稳定、成本最低的人类客服分流依据。

用户通常从商品页或店铺页发起咨询，请求天然携带 `shopId`。

主路径：

```text
用户从商品页 / 店铺页发起会话
  ↓
网关透传 businessLine + 请求携带 shopId
  ↓
按 shopId 查店，核对网关 businessLine 与 mall_shop.business_line（不一致则 error 日志，见 5.1）
  ↓
以网关 businessLine + shopId 进入店铺客服小组；tenantId 可落会话元数据
  ↓
在该小组内分配客服
  ↓
机器人进入群聊辅助
```

核心分配逻辑（与「第五节」一致，此处是店铺视角的摘要）：

```text
网关 businessLine + shopId（与库表核对） → 店铺客服组（人↔店） → 具体客服
```

组内选人规则（负载等；**离线仍可绑定售前**）见上文第五节；店铺未配置绑定人时的同业务线运营兜底见上文第六节（阶段 2）；**售前不由 corporate 顶替**；机器人文案见第二节。

第一阶段不需要让用户选择“售前 / 售后 / 税务 / 法律”，也不需要让 AI 判断所有咨询类型。

---

## 十、店铺客服忙不过来 / 不在线怎么办

当前方案不把“客服忙不过来”设计成**改派到其他店铺或 corporate** 的路由问题。

原则是：

> **只要会话带 shopId，就优先并稳定归属到该店铺客服小组（且在已解析的 `businessLine` 边界内）；售前离线或忙碌时仍绑定该组，由机器人提示客户等待；系统不因为忙碌自动跨店铺分配，更不跨 `businessLine` 混派，也不让 corporate 接店铺售前。**

如果某个店铺咨询量变大，说明这个店铺需要扩充服务能力。

处理方式：

```text
给该店铺增加客服人数
增加客服排班
机器人：工作时间内提示「客服忙碌」（时间窗口内去重）
让 AI 先回答标准问题
优化商品详情页，减少重复咨询
沉淀 FAQ
后期拆分售前 / 售后角色
```

不设计成：

```text
店铺售前全员离线
  ↓
自动改派 corporate 或别的店售前

店铺客服满载
  ↓
自动转其他店铺客服
```

因为这样会破坏店铺服务小组的稳定性，也会让不熟悉该店铺商品的客服（或法务坐席）接入商品/订单类咨询。

---

## 十一、AI 的核心定位

第一阶段，AI 不负责复杂分诊，但它仍然是客服体系提效的关键。

AI 的定位是：

> **群聊助手 + 标准问题处理器 + 商品知识库检索器 + 客服外挂大脑。**

知识检索与辅助输出宜受 **`tenantId` + `businessLine` + `shopId`**（及关联 `productId`）约束，见上文第七节（**与人类进线选人规则分离**）。

AI 不主导判断：

```text
这是售前还是售后
这是法律还是税务
是否应该退款
是否应该投诉升级
应该派给哪个复杂坐席
```

AI 主要负责：

```text
标准 FAQ 自动回答
商品基础问题回答
订单 / 物流 / 退换货规则解释
多语言翻译
客服建议回复
总结用户问题
提取订单号 / 商品 / SKU
检索商品知识库
生成会话摘要
沉淀高频问题
辅助客服快速理解上下文
```

也就是说：

```text
AI = 群聊助手
AI = 客服外挂大脑
AI ≠ 第一阶段派单主脑
AI ≠ 售后责任判断者
```

---

## 十二、AI 能分担的三类压力

AI 主要分担三类压力。

### 12.1 重复咨询压力

典型问题：

```text
物流到哪里了？
什么时候发货？
怎么退货？
能不能换货？
优惠券怎么用？
支持什么支付方式？
发票怎么开？
```

这类问题规则明确，适合 AI 先回答。

如果用户不满意，或者涉及异常售后，再转人工。

---

### 12.2 基础商品问答压力

典型问题：

```text
这个商品是什么材质？
有没有黑色？
尺寸是多少？
适合几岁孩子？
包装里有什么？
支持某个型号吗？
库存还有吗？
```

这类问题可以通过商品结构化数据、FAQ、规格参数、历史问答让 AI 先回答。

但如果涉及复杂适配、风险承诺、高客单价成交，仍然由店铺客服处理。

---

### 12.3 人工客服记忆压力

店铺客服不可能完全靠脑子记住所有商品细节。

AI 可以帮助客服快速检索：

```text
商品参数
适用场景
禁用场景
售后风险
历史差评原因
退货原因
用户常问问题
推荐话术
禁止承诺话术
```

例如用户问：

```text
这个户外电源能不能带动 800W 电饭煲？
```

AI 可以先帮客服检索：

```text
商品额定功率
峰值功率
电饭煲启动功率
用户评价
售后风险
品类负责人维护的注意事项
```

然后给客服生成建议回复。

最终由客服判断是否直接发送、修改后发送，还是拉运营负责人 / 品类专家入群。

---

## 十三、AI 对客服效率的价值

AI 不一定要替代人，也能产生明显价值。

它主要提高三个效率：

```text
1. 响应效率
2. 处理效率
3. 培训效率
```

### 13.1 响应效率

AI 可以在人工客服回复前先做：

```text
欢迎语
标准问题回答
问题收集
订单号收集
商品信息提取
基础安抚
```

这样用户不会感觉没人理。

---

### 13.2 处理效率

AI 可以帮助客服：

```text
总结用户前文
提取核心诉求
推荐回复话术
检索商品知识
翻译用户语言
生成多语言回复
提示风险话术
```

客服不用从头翻聊天记录，也不用自己搜索商品参数。

---

### 13.3 培训效率

新客服不需要完全靠人工培训记住所有商品。

只要商品知识库、FAQ、售后规则逐步沉淀，AI 就能辅助新客服快速上手。

这会降低：

```text
培训成本
质检成本
客服犯错率
对老客服经验的依赖
```

---

## 十四、AI 提效的现实预期

AI 不能让客服消失，但可以让客服团队扩张更慢。

用户量上来以后，客服人数仍会增加，但不必跟用户量同比例增长。

例如：

```text
无 AI：
用户量增长 10 倍
客服人数可能也接近增长 10 倍

有 AI + FAQ + 知识库：
用户量增长 10 倍
客服人数可能只增长 2–4 倍
```

在实际客服压力中，AI 更适合分担：

```text
物流 / 订单 / 退换货规则
商品基础参数
常见售前问题
多语言翻译
客服建议回复
会话摘要
```

不适合独立处理：

```text
投诉
纠纷
差评
赔付
法律税务责任
复杂售后
高金额订单争议
```

你的原文里也提到，AI 对物流、订单、退换货、基础商品问答的分担比例更高，但对投诉、纠纷、赔付这类场景必须人工把关。

---

## 十五、售前、售后、税务、法律怎么处理

第一阶段不要让用户自己选，也不要让 AI 强行判断。

主路径是：

```text
用户带 shopId 进来
  ↓
进入店铺客服组
  ↓
店铺客服判断问题性质
```

店铺客服根据情况处理：

```text
普通商品问题 → 自己处理
售后问题 → 拉售后 / 主管入群
税务法律问题 → 拉平台负责人 / 老板 / 合规人员入群
复杂商品问题 → 拉运营负责人 / 品类负责人入群
投诉问题 → 拉主管 / 老板入群
```

这正好利用群聊模型：用户、客服、运营负责人、主管、机器人可以在同一个服务房间里协作。

---

## 十六、商品知识库是 AI 提效的前提

AI 能不能提效，核心不在模型本身，而在商品数据和知识库质量。

每个商品应逐步沉淀：

```text
商品核心卖点
适用人群
不适用场景
尺寸 / 规格 / 材质
适配范围
禁用场景
包装清单
售后规则
常见问题
用户评价摘要
差评原因
退货原因
竞品对比
安装 / 使用教程
推荐话术
禁止承诺话术
```

如果商品数据混乱，AI 只会更快地输出错误答案。

所以 AI 建设要和商品中心、知识库、FAQ、用户评价总结一起推进。

---

## 十七、主接待与群成员

客服会话中区分两个概念：

```text
主接待人
参与成员
```

主接待人表示：

```text
当前这个会话主要由谁负责
```

群成员表示：

```text
谁参与了这个群聊
```

软件视图可以这样理解：

```text
主接待客服：
查看自己主负责的会话

被拉进群的售后 / 主管 / 专家：
查看自己参与的会话

主管 / 老板：
查看无 shopId、无客服、异常、投诉等兜底会话
```

原则：

```text
谁主接待，看主接待列表
谁参与协作，看参与会话列表
谁负责兜底，看异常和未配置客服接待列表
```

不要为了让所有角色都能看到会话，把多个客服 ID 都塞进主会话字段里。

### mall-chat HTTP 约定（客服端）

请求头须带 `X-Business-Line`（与网关枚举一致）、`X-User-Id`（当前客服 ID）。路径前缀：`/chat/agent-service/conversation`。

| 能力 | 接口 | 数据来源 | 说明 |
|------|------|----------|------|
| 店铺主接待列表 | `POST /listConversations` | `agent_id = 当前客服` 且 **`shop_id` 非空** | 售前/售后店铺主接待 |
| **未配置客服接待（店铺）** | `POST /listConversationsWithoutConfiguredAgentReception` | 有 `shop_id`、`agent_id IS NULL`、`waiting`；**仅绑定店铺** | **售前**抢接待；售后不调 |
| 参与协作会话列表 | `POST /listParticipantConversations` | `chat_conversation_member` 联表 | 售后/法务等主工作区；**排除**主接待 |
| **公司级（无 shopId）** | `POST /listCorporateConversations` | `shop_id IS NULL`：主接待 + 未分配 waiting | **老板/corporate**；售前不调 |
| 登录聊天窗口预览 | `POST /getChatWindowList` | 店铺主接待 + 参与协作 | 不含未配置接待、不含公司级 |
| 角色与前端调用矩阵 | 见 `doc/客服端会话列表接口与角色调用说明.md` | — | — |
| 接待/协作加入 | `POST /joinConversation` | **未配置客服接待**：写 `agent_id`+`active` 并加成员；**已有主接待**：仅协作进群 | 须为本店绑定售前 |
| 邀请进群 | 应用内 `inviteToConversation` | 同上 | 协作成员只进成员表 |

数据写入：

- 客户新进线：路由选出主接待 → 写 `chat_conversation.agent_id`，并把该客服写入 `chat_conversation_member`（与主接待为同一人，不是双写多个主接待）。
- 售后/主管被拉进群：只写 `chat_conversation_member`（含冗余 `business_line`），**禁止**为协作成员改 `agent_id`。
- `chat_conversation_member` 增删查改须带 `business_line`（与 `chat_conversation` 一致）；增量 DDL 见 `doc/modify.sql`（2026-05-19）。

---

## 十八、分阶段升级

### 阶段 1：按店铺分配客服（`businessLine` + `shopId`）

先做到：

```text
客服与 shopId 通过关系表绑定，并归属 businessLine
用户带 shopId 进来
解析 businessLine，在店铺绑定池内选人（不按 tenantId 划池）
优先分配给该 shopId 下的客服小团队（不跨 businessLine）
```

组内按简单规则自动选择客服：

```text
在线状态
当前会话数
是否可接待
```

---

### 阶段 2：补充兜底机制

处理几类情况：

```text
无 shopId 但有 businessLine
有 shopId 但店铺没有客服
（无 businessLine 的请求不进入本阶段兜底，见 5.3）
```

兜底到（与上文第五节、第六节一致，不跨 businessLine 混派）：

```text
有 businessLine、无 shopId → 业务线兜底组 / corporate 等（见 5.2）
仅有 shopId 但店铺未配置任何绑定人 → 同 businessLine 运营兜底（主管 / 老板），不是 corporate 顶替售前（阶段 2）
店铺有绑定售前但均离线 → 仍绑售前 + 机器人按工时发忙碌/非工时提示（见第二节）
```

---

### 阶段 3：AI 入群辅助

AI 先做：

```text
FAQ
翻译
摘要
建议回复
知识库检索
标准问题回答
订单 / 物流 / 售后规则解释
```

不做复杂路由主责。

---

### 阶段 4：建立商品知识库

每个商品逐步沉淀：

```text
商品参数
FAQ
售后规则
用户常问问题
禁止承诺话术
运营负责人补充说明
历史客服问答
评价和退货原因总结
```

这部分是 AI 提效的基础。

---

### 阶段 5：人工升级协作

由店铺客服判断是否需要拉人：

```text
售后问题 → 拉售后 / 主管
税务法律 → 拉平台负责人 / 老板
复杂商品问题 → 拉运营负责人 / 品类负责人
投诉问题 → 拉主管 / 老板
```

---

### 阶段 6：后期再做 AI 分流测试

等业务量和聊天数据足够后，再测试：

```text
AI 判断售前/售后是否准确
AI 是否能识别退款/投诉风险
AI 是否能推荐升级对象
AI 是否能降低人工判断成本
AI 是否能提前识别高风险会话
```

如果效果好，再逐步让 AI 参与部分分流。

---

## 十九、客服效率指标

后期可以重点观察：

```text
AI 自动回答命中率
AI 建议回复采纳率
人工介入率
平均首次响应时间
平均处理时长
单客服日处理会话数
单客服支撑订单数
用户满意度
转人工后的解决率
投诉率
退款率
差评率
```

目标不是让 AI 替代客服，而是提高客服杠杆率。

例如：

```text
没有 AI：
1 个客服只能支撑较少订单和咨询

有 AI + 知识库：
1 个客服可以支撑更多订单、更多咨询、更多品类
```

你原文中也把“每个客服能支撑多少订单 / GMV / 用户咨询”作为客服杠杆率指标，并强调 AI + 知识库 + 自助系统可以提高单客服承载量。

---

## 二十、最终判断

当前阶段最优方案是：

```text
公司统一管理客服
businessLine 代表业务域隔离边界（人类客服池与 AI 知识不混用）
shopId 作为具体店铺分流主依据（人↔店绑定）
有 shopId 时在 businessLine + 店铺小组内选人；不按 tenantId 分配
无 shopId 但有 businessLine 时进该业务线兜底组（平台/法务类可用 corporate）
无 businessLine 时不进线、不落库（见 5.3）
有 shopId：售前仍从店铺绑定池选；离线由机器人提示忙碌，不跨业务线、不由 corporate 顶替售前
tenantId 可落会话元数据；AI 宜在 tenantId + businessLine + shopId 限定内检索与辅助
复杂问题由人类判断并拉人协作
```

最终一句话（与上文第八节一致）：

> **客服由公司统一雇佣和建号；`businessLine` 为进线必选（无业务线不落库、不分配）。`shopId` 表示具体店铺分流入口。有 `shopId` 的会话在 `businessLine` + 店铺绑定池内选售前；售前离线仍归该组并由机器人提示忙碌，`corporate` 不顶替店铺售前。无 `shopId` 但有 `businessLine` 的会话进入该业务线或平台类兜底（如 corporate）。`tenantId` 可随店铺落库，不参与人类选人。AI 宜在 `tenantId` + `businessLine` + `shopId` 限定范围内做标准问答、知识库检索、翻译、摘要和客服辅助。**

---

## 二十一、mall-chat 当前已实现功能清单（与本文档对照）

> 本节描述 **mall-chat 仓库当前代码 + `doc/modify.sql` 增量 DDL** 已交付能力，便于与上文产品/分阶段叙述区分：**未列入下表的 AI 知识库、FAQ 自动答、翻译摘要、阶段 6 分流等仍为规划，不在本服务实现范围内。**

### 21.1 人类客服进线与分配（阶段 1 主体）

| 能力 | 实现要点 |
|------|----------|
| 业务线必选 | 无有效 `businessLine` **不落库**、不分配（见 5.3）；网关须透传 `X-Business-Line` |
| 业务线硬隔离 | 选人、店铺关系、客服表查询均以 `business_line` 为条件 |
| 不按 tenant 选人 | `tenant_id` 可写入 `chat_conversation`（来自 `mall_shop`），**不参与**分配 SQL |
| 有店：店铺售前池 | `chat_agent_shop_relation` + `AgentManagementService#resolveShopPreSalesAgent`：仅 `pre-sales`；优先在线负载最低，否则离线负载最低 |
| 有店：不回退 corporate | 有 `shopId` 时**不会**因池空或离线改派 `corporate` |
| 无店：公司级池 | 无 `shopId` 时 `resolveCorporateAgent`（`agent_type=corporate`），用于无店铺/平台类入口 |
| 未配置客服接待 | 店铺售前池空等导致 `agent_id=null`、`status=waiting`；`listConversationsWithoutConfiguredAgentReception` 列表，`joinConversation` 接待 |
| 主接待 + 群成员 | 进线写 `chat_conversation.agent_id` 并写入 `chat_conversation_member`（客户、主接待售前、机器人 `666666`） |
| 路由绑定 | `AgentRoutingServiceImpl`：`bindInboundAgent` 同步主接待；已存在会话按成员表判断是否重分配 |
| 店铺业务线核对 | `MallShopService#assertShopBusinessLineMatchesGateway`：有 `shopId` 时核对网关 `X-Business-Line` 与 `mall_shop.business_line`；不一致/店不存在打 `[SHOP_BUSINESS_LINE_MISMATCH]` / `[SHOP_ROUTING]` error 日志（`[TODO: alerting]`）；选人边界仍以网关业务线为准，当前不阻断链路 |

### 21.2 数据模型与业务线冗余

| 表 / 字段 | 说明 |
|-----------|------|
| `chat_message.business_line` | 落库必填（`UserContextService#applyBusinessLineForPersist`）；增量见 `modify.sql` 2026-05-18 |
| `chat_conversation_member.business_line` | 成员冗余业务线；增删查改带 `business_line`；增量见 `modify.sql` 2026-05-19、2026-05-20 索引规范 |
| `chat_agent_shop_relation` | 客服↔店铺多对多；索引最左为 `business_line` |
| 索引规范 | `chat_message` / `chat_conversation_member` 复合索引最左列为 `business_line`（`modify.sql` 2026-05-20） |

### 21.3 客服端 HTTP 与会话视图（第十七节）

| 接口 | 路径（前缀 `/chat/agent-service/conversation`） | 行为 |
|------|-----------------------------------------------|------|
| 店铺主接待 | `POST /listConversations` | `agent_id = 当前客服` 且 `shop_id` 非空 |
| 未配置客服接待 | `POST /listConversationsWithoutConfiguredAgentReception` | 售前；有店、`agent_id` 空、`waiting` |
| 参与协作 | `POST /listParticipantConversations` | 售后/法务等；成员表、非主接待 |
| 公司级 | `POST /listCorporateConversations` | 老板；`shop_id` 空 |
| 登录预览 | `POST /getChatWindowList` | 店铺主接待 + 协作 |
| 前端角色说明 | `doc/客服端会话列表接口与角色调用说明.md` | 按 `agentType` 决定调哪些接口 |
| 接待/协作加入 | `POST /joinConversation` | 未配置客服接待时写主接待；否则仅协作进群 |
| 邀请进群 | `AgentHttpService#inviteToConversation` | 同上（服务层已实现；**未**单独暴露 HTTP Controller） |
| 其它 | 分页拉消息、删会话、缺消息检查、标记已读等 | 已存在；部分读路径仍仅按 `conversation_id`，后续可按需补 `business_line` 条件 |

### 21.4 机器人与消息

| 能力 | 说明 |
|------|------|
| 忙碌 / 非工时提示 | `RobotAgentService#sendAutoReplyMessage`：按配置工作时间选文案；Redis 窗口去重 |
| 机器人入群 | 成员 `robot_agent` / `666666`；机器人消息带会话 `business_line` |
| 无业务线 | 无有效 `businessLine` 时**不落库**、跳过会话分配与客服推送（`UserContextService`、`handleConversation`）；网关应保证 `X-Business-Line` |

### 21.5 明确未实现 / 待后续（勿与上文「已实现」混淆）

```text
店铺绑定池为空 → 同 businessLine 运营/主管兜底（当前 agent_id 为空，见阶段 2）
无 shopId 仅有 businessLine → 除 corporate 外的业务线默认负责人池（阶段 2）
AI：FAQ 自动答、知识库检索、翻译、摘要、建议回复、按 tenant+BL+shop 限域检索
商品知识库、客服效率指标（第十九节）、阶段 6 AI 分流
inviteToConversation 的独立 HTTP 接口（若产品需要）
部分历史消息/会话查询在 SQL 中未强制带 business_line（与索引规范可继续对齐）
```

### 21.6 文档维护约定

- **表结构变更**：只追加在 `doc/modify.sql` 文件**最底部**，不在 `init.sql` 建表语句中插队改字段/索引。  
- **实现状态**：以本节与第二节「偏差表」为准；代码变更后请同步更新本节与偏差表，避免重复记录已修复项。
