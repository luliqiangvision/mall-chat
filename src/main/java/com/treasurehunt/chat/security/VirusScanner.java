package com.treasurehunt.chat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 病毒扫描器
 * 集成ClamAV或其他杀毒引擎进行文件扫描
 */
@Slf4j
@Component
public class VirusScanner {

    @Value("${chat.virus-scan.enabled:true}")
    private boolean virusScanEnabled;
    
    @Value("${chat.virus-scan.engine:clamav}")
    private String scanEngine;
    
    @Value("${chat.virus-scan.timeout:30}")
    private int scanTimeoutSeconds;
    
    @Value("${chat.virus-scan.temp-dir:/tmp/chat-uploads}")
    private String tempDir;
    
    /**
     * 异步扫描文件
     */
    public CompletableFuture<ScanResult> scanFileAsync(MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return scanFile(file);
            } catch (Exception e) {
                log.error("文件扫描异常", e);
                return ScanResult.error("扫描服务异常");
            }
        });
    }
    
    /**
     * 同步扫描文件
     */
    public ScanResult scanFile(MultipartFile file) {
        if (!virusScanEnabled) {
            log.info("病毒扫描已禁用，跳过扫描");
            return ScanResult.clean();
        }
        
        if (file == null || file.isEmpty()) {
            return ScanResult.error("文件为空");
        }
        
        Path tempFile = null;
        try {
            // 创建临时文件
            tempFile = createTempFile(file);
            
            // 根据配置选择扫描引擎
            ScanResult result;
            switch (scanEngine.toLowerCase()) {
                case "clamav":
                    result = scanWithClamAV(tempFile);
                    break;
                case "mock":
                    result = mockScan(tempFile);
                    break;
                default:
                    log.warn("未知的扫描引擎: {}, 使用模拟扫描", scanEngine);
                    result = mockScan(tempFile);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("文件扫描失败", e);
            return ScanResult.error("扫描失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tempFile, e);
                }
            }
        }
    }
    
    /**
     * 使用ClamAV扫描
     */
    private ScanResult scanWithClamAV(Path file) {
        try {
            // 构建ClamAV命令
            ProcessBuilder pb = new ProcessBuilder(
                "clamdscan", 
                "--no-summary", 
                "--infected", 
                file.toString()
            );
            
            Process process = pb.start();
            
            // 等待扫描完成，设置超时
            boolean finished = process.waitFor(scanTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ScanResult.error("扫描超时");
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode == 0) {
                log.info("文件扫描通过: {}", file.getFileName());
                return ScanResult.clean();
            } else if (exitCode == 1) {
                log.warn("检测到病毒文件: {}", file.getFileName());
                return ScanResult.infected("检测到病毒");
            } else {
                log.error("ClamAV扫描异常，退出码: {}", exitCode);
                return ScanResult.error("扫描服务异常");
            }
            
        } catch (Exception e) {
            log.error("ClamAV扫描失败", e);
            return ScanResult.error("扫描失败: " + e.getMessage());
        }
    }
    
    /**
     * 模拟扫描（用于测试或ClamAV不可用时）
     */
    private ScanResult mockScan(Path file) {
        try {
            // 模拟扫描延迟
            Thread.sleep(1000);
            
            String fileName = file.getFileName().toString().toLowerCase();
            
            // 模拟检测逻辑
            if (fileName.contains("virus") || fileName.contains("malware")) {
                return ScanResult.infected("模拟检测到病毒");
            }
            
            // 检查文件大小，大文件可能有问题
            long fileSize = Files.size(file);
            if (fileSize > 100 * 1024 * 1024) { // 100MB
                return ScanResult.suspicious("文件过大，需要人工检查");
            }
            
            return ScanResult.clean();
            
        } catch (Exception e) {
            log.error("模拟扫描失败", e);
            return ScanResult.error("扫描失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建临时文件
     */
    private Path createTempFile(MultipartFile file) throws IOException {
        // 确保临时目录存在
        Path tempDirPath = Paths.get(tempDir);
        if (!Files.exists(tempDirPath)) {
            Files.createDirectories(tempDirPath);
        }
        
        // 创建临时文件
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        Path tempFile = Files.createTempFile(tempDirPath, "scan_", extension);
        
        // 写入文件内容
        Files.write(tempFile, file.getBytes());
        
        return tempFile;
    }
    
    /**
     * 扫描结果
     */
    public static class ScanResult {
        private final ScanStatus status;
        private final String message;
        private final long scanTime;
        
        private ScanResult(ScanStatus status, String message) {
            this.status = status;
            this.message = message;
            this.scanTime = System.currentTimeMillis();
        }
        
        public static ScanResult clean() {
            return new ScanResult(ScanStatus.CLEAN, "文件安全");
        }
        
        public static ScanResult infected(String message) {
            return new ScanResult(ScanStatus.INFECTED, message);
        }
        
        public static ScanResult suspicious(String message) {
            return new ScanResult(ScanStatus.SUSPICIOUS, message);
        }
        
        public static ScanResult error(String message) {
            return new ScanResult(ScanStatus.ERROR, message);
        }
        
        public boolean isClean() {
            return status == ScanStatus.CLEAN;
        }
        
        public boolean isInfected() {
            return status == ScanStatus.INFECTED;
        }
        
        public boolean isSuspicious() {
            return status == ScanStatus.SUSPICIOUS;
        }
        
        public boolean isError() {
            return status == ScanStatus.ERROR;
        }
        
        public ScanStatus getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public long getScanTime() {
            return scanTime;
        }
        
        @Override
        public String toString() {
            return String.format("ScanResult{status=%s, message='%s', scanTime=%d}", 
                    status, message, scanTime);
        }
    }
    
    /**
     * 扫描状态枚举
     */
    public enum ScanStatus {
        CLEAN,      // 文件安全
        INFECTED,   // 检测到病毒
        SUSPICIOUS, // 可疑文件
        ERROR       // 扫描错误
    }
}
