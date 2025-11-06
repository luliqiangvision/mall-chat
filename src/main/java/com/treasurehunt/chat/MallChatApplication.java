package com.treasurehunt.chat;

import com.treasurehunt.chat.framework.core.websocket.mvc.config.EnableWebSocketMvc;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.ZoneId;

@EnableDiscoveryClient
@EnableAsync
@EnableWebSocketMvc(basePackages = {"com.treasurehunt.chat.controller"})
@MapperScan("com.treasurehunt.chat.mapper")
@SpringBootApplication(scanBasePackages = {"com.treasurehunt.chat"})
public class MallChatApplication {
    private static final Logger log = LoggerFactory.getLogger(MallChatApplication.class);

    public static void main(String[] args) {
        log.info("当前服务器运行时区" + ZoneId.systemDefault());
        SpringApplication.run(MallChatApplication.class, args);
    }
}
