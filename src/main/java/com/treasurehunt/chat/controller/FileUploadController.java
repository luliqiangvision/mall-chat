package com.treasurehunt.chat.controller;

import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.security.FileUploadSecurityFilter;
import com.treasurehunt.chat.security.VirusScanner;
import com.treasurehunt.chat.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文件上传控制器
 * 处理聊天中的文件上传，包含完整的安全检查流程
 */
@Slf4j
@RestController
@RequestMapping("/chat/file")
@Tag(name = "文件上传", description = "聊天文件上传接口")
public class FileUploadController {

    @Autowired
    private FileUploadSecurityFilter securityFilter;
    
    @Autowired
    private VirusScanner virusScanner;
    
    @Autowired
    private FileStorageService fileStorageService;

    @Operation(summary = "上传文件", description = "上传聊天文件，包含安全检查")
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId,
            @RequestParam("toUserId") String toUserId,
            @RequestParam(value = "messageType", defaultValue = "file") String messageType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("开始处理文件上传: 用户={}, 文件={}, 大小={}", 
                    userId, file.getOriginalFilename(), file.getSize());
            
            // 1. 基础安全检查
            FileUploadSecurityFilter.FileSecurityResult securityResult = securityFilter.validateFile(file);
            if (!securityResult.isValid()) {
                log.warn("文件安全检查失败: {}", securityResult.getReason());
                response.put("success", false);
                response.put("message", securityResult.getReason());
                return ResponseEntity.badRequest().body(response);
            }
            
            // 2. 病毒扫描
            VirusScanner.ScanResult scanResult = virusScanner.scanFile(file);
            if (scanResult.isInfected()) {
                log.warn("检测到病毒文件: 用户={}, 文件={}, 原因={}", 
                        userId, file.getOriginalFilename(), scanResult.getMessage());
                response.put("success", false);
                response.put("message", "文件包含病毒，无法上传");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (scanResult.isSuspicious()) {
                log.warn("检测到可疑文件: 用户={}, 文件={}, 原因={}", 
                        userId, file.getOriginalFilename(), scanResult.getMessage());
                response.put("success", false);
                response.put("message", "文件可疑，需要人工审核");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (scanResult.isError()) {
                log.error("病毒扫描失败: 用户={}, 文件={}, 错误={}", 
                        userId, file.getOriginalFilename(), scanResult.getMessage());
                response.put("success", false);
                response.put("message", "文件扫描失败，请稍后重试");
                return ResponseEntity.status(500).body(response);
            }
            
            // 3. 存储文件
            String fileUrl = fileStorageService.storeFile(file, userId);
            if (fileUrl == null) {
                log.error("文件存储失败: 用户={}, 文件={}", userId, file.getOriginalFilename());
                response.put("success", false);
                response.put("message", "文件存储失败");
                return ResponseEntity.status(500).body(response);
            }
            
            // 4. 创建聊天消息
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setType(messageType);
            chatMessage.setFromUserId(userId);
            chatMessage.setContent(file.getOriginalFilename());
            chatMessage.setTimestamp(new java.util.Date());
            
            // 5. 返回成功响应
            response.put("success", true);
            response.put("message", "文件上传成功");
            response.put("fileUrl", fileUrl);
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("chatMessage", chatMessage);
            
            log.info("文件上传成功: 用户={}, 文件={}, URL={}", 
                    userId, file.getOriginalFilename(), fileUrl);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("文件上传异常: 用户={}, 文件={}", userId, file.getOriginalFilename(), e);
            response.put("success", false);
            response.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(summary = "异步上传文件", description = "异步上传文件，适用于大文件")
    @PostMapping("/upload-async")
    public ResponseEntity<Map<String, Object>> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId,
            @RequestParam("toUserId") String toUserId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. 基础安全检查
            FileUploadSecurityFilter.FileSecurityResult securityResult = securityFilter.validateFile(file);
            if (!securityResult.isValid()) {
                response.put("success", false);
                response.put("message", securityResult.getReason());
                return ResponseEntity.badRequest().body(response);
            }
            
            // 2. 异步病毒扫描
            CompletableFuture<VirusScanner.ScanResult> scanFuture = virusScanner.scanFileAsync(file);
            
            // 3. 先存储到临时区域
            String tempFileUrl = fileStorageService.storeTempFile(file, userId);
            
            // 4. 返回临时响应
            response.put("success", true);
            response.put("message", "文件已上传，正在扫描中...");
            response.put("tempFileUrl", tempFileUrl);
            response.put("scanning", true);
            
            // 5. 异步处理扫描结果
            scanFuture.thenAccept(scanResult -> {
                if (scanResult.isClean()) {
                    // 扫描通过，移动到正式存储
                    String finalFileUrl = fileStorageService.moveToFinalStorage(tempFileUrl, userId);
                    log.info("异步文件扫描通过: 用户={}, 文件={}, 最终URL={}", 
                            userId, file.getOriginalFilename(), finalFileUrl);
                } else {
                    // 扫描失败，删除临时文件
                    fileStorageService.deleteTempFile(tempFileUrl);
                    log.warn("异步文件扫描失败: 用户={}, 文件={}, 原因={}", 
                            userId, file.getOriginalFilename(), scanResult.getMessage());
                }
            });
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("异步文件上传异常: 用户={}, 文件={}", userId, file.getOriginalFilename(), e);
            response.put("success", false);
            response.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(summary = "获取文件下载链接", description = "生成带签名的下载链接")
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Map<String, Object>> getDownloadUrl(@PathVariable String fileId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String downloadUrl = fileStorageService.generateDownloadUrl(fileId);
            if (downloadUrl == null) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.notFound().build();
            }
            
            response.put("success", true);
            response.put("downloadUrl", downloadUrl);
            response.put("expiresIn", 300); // 5分钟有效期
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("生成下载链接失败: fileId={}", fileId, e);
            response.put("success", false);
            response.put("message", "生成下载链接失败");
            return ResponseEntity.status(500).body(response);
        }
    }
}
