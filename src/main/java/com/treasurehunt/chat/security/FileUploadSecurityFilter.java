package com.treasurehunt.chat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文件上传安全过滤器，需要注意的是，客户如果想上传文件，只能使用前端app或者网页调用摄像头拍照或者录视频，而且不允许使用客户收集里的文件，防止客户上传病毒文件
 * 调用摄像头的时候，看看能不能加水印或者其他东西，这样能鉴别出来是我们承认的才行，也就是前端的js可能要参与水印的添加
 * 用于检测和过滤恶意文件，防止病毒传播
 */
@Slf4j
@Component
public class FileUploadSecurityFilter {

    // 允许的文件类型白名单 - 只允许图片、文字、视频
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
        // 图片文件
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
        // 文字文件
        "txt", "md", "rtf",
        // 视频文件
        "mp4", "avi", "mov", "wmv", "flv", "webm", "mkv"
    );
    
    // 禁止的文件类型黑名单
    private static final List<String> FORBIDDEN_EXTENSIONS = Arrays.asList(
        // 可执行文件
        "exe", "bat", "cmd", "com", "scr", "pif", "vbs", "js", "jar",
        // 系统文件
        "dll", "sys", "drv", "ocx", "cpl",
        // 脚本文件
        "sh", "ps1", "py", "pl", "rb", "php", "asp", "jsp",
        // 其他危险文件
        "apk", "ipa", "deb", "rpm", "msi", "app", "dmg"
    );
    
    // 文件大小限制（字节）
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_TEXT_SIZE = 10 * 1024; // 10KB - 客户手打字200字左右
    private static final long MAX_VIDEO_SIZE = 15 * 1024 * 1024; // 15MB
    
    // 恶意文件特征检测
    private static final List<Pattern> MALICIOUS_PATTERNS = Arrays.asList(
        // PE文件头检测
        Pattern.compile("MZ.*PE", Pattern.DOTALL),
        // 脚本文件检测
        Pattern.compile("#!/bin/(bash|sh|python|perl)", Pattern.CASE_INSENSITIVE),
        // 宏病毒检测
        Pattern.compile("Sub\\s+AutoOpen|Sub\\s+Document_Open", Pattern.CASE_INSENSITIVE),
        // 恶意URL检测
        Pattern.compile("(https?://)?(www\\.)?[a-zA-Z0-9.-]*\\.(onion|bit|tk|ml|ga|cf)", Pattern.CASE_INSENSITIVE)
    );
    
    /**
     * 验证文件安全性
     */
    public FileSecurityResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return FileSecurityResult.invalid("文件为空");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return FileSecurityResult.invalid("文件名不能为空");
        }
        
        // 检查文件大小
        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            return FileSecurityResult.invalid("文件大小超过限制（最大100MB）");
        }
        
        // 获取文件扩展名
        String extension = getFileExtension(originalFilename);
        if (extension == null) {
            return FileSecurityResult.invalid("文件扩展名不能为空");
        }
        
        // 检查黑名单
        if (FORBIDDEN_EXTENSIONS.contains(extension.toLowerCase())) {
            log.warn("检测到禁止的文件类型: {}", extension);
            return FileSecurityResult.invalid("不允许上传此类型文件");
        }
        
        // 检查白名单
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.warn("检测到未授权的文件类型: {}", extension);
            return FileSecurityResult.invalid("不支持此文件类型");
        }
        
        // 检查文件大小限制（按类型）
        if (!checkFileSizeByType(extension, fileSize)) {
            return FileSecurityResult.invalid("文件大小超过该类型限制");
        }
        
        // 检查文件名安全性
        if (!isSafeFilename(originalFilename)) {
            return FileSecurityResult.invalid("文件名包含不安全字符");
        }
        
        // 检查文件内容
        try {
            FileSecurityResult contentResult = validateFileContent(file, extension);
            if (!contentResult.isValid()) {
                return contentResult;
            }
        } catch (IOException e) {
            log.error("文件内容验证失败", e);
            return FileSecurityResult.invalid("文件内容验证失败");
        }
        
        return FileSecurityResult.valid();
    }
    
    /**
     * 验证文件内容
     */
    private FileSecurityResult validateFileContent(MultipartFile file, String extension) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            StringBuilder content = new StringBuilder();
            
            // 读取文件头部内容进行检测
            while ((bytesRead = inputStream.read(buffer)) != -1 && content.length() < 4096) {
                content.append(new String(buffer, 0, bytesRead));
            }
            
            String fileContent = content.toString();
            
            // 检查恶意模式
            for (Pattern pattern : MALICIOUS_PATTERNS) {
                if (pattern.matcher(fileContent).find()) {
                    log.warn("检测到恶意文件内容: {}", pattern.pattern());
                    return FileSecurityResult.invalid("文件内容包含恶意代码");
                }
            }
            
            // 检查文件头魔数
            if (!validateFileHeader(buffer, extension)) {
                return FileSecurityResult.invalid("文件类型与扩展名不匹配");
            }
            
        }
        
        return FileSecurityResult.valid();
    }
    
    /**
     * 验证文件头魔数 - 严格校验
     */
    private boolean validateFileHeader(byte[] buffer, String extension) {
        if (buffer.length < 8) {
            return false;
        }
        
        String lowerExt = extension.toLowerCase();
        
        // 严格检查文件类型的魔数
        switch (lowerExt) {
            // JPEG图片
            case "jpg":
            case "jpeg":
                return buffer[0] == (byte) 0xFF && buffer[1] == (byte) 0xD8 && 
                       buffer[2] == (byte) 0xFF;
            
            // PNG图片
            case "png":
                return buffer[0] == (byte) 0x89 && buffer[1] == 0x50 && 
                       buffer[2] == 0x4E && buffer[3] == 0x47 &&
                       buffer[4] == 0x0D && buffer[5] == 0x0A && 
                       buffer[6] == 0x1A && buffer[7] == 0x0A;
            
            // GIF图片
            case "gif":
                return (buffer[0] == 0x47 && buffer[1] == 0x49 && buffer[2] == 0x46) &&
                       (buffer[3] == 0x38 && (buffer[4] == 0x37 || buffer[4] == 0x39) && buffer[5] == 0x61);
            
            // BMP图片
            case "bmp":
                return buffer[0] == 0x42 && buffer[1] == 0x4D;
            
            // WebP图片
            case "webp":
                return buffer[0] == 0x52 && buffer[1] == 0x49 && 
                       buffer[2] == 0x46 && buffer[3] == 0x46 &&
                       buffer[8] == 0x57 && buffer[9] == 0x45 && 
                       buffer[10] == 0x42 && buffer[11] == 0x50;
            
            // SVG图片 (XML格式)
            case "svg":
                return buffer[0] == 0x3C && buffer[1] == 0x3F && 
                       buffer[2] == 0x78 && buffer[3] == 0x6D && 
                       buffer[4] == 0x6C; // <?xml
            
            // 文本文件
            case "txt":
            case "md":
                // 文本文件没有固定魔数，检查是否为可打印字符
                return isTextFile(buffer);
            
            // RTF文件
            case "rtf":
                return buffer[0] == 0x7B && buffer[1] == 0x5C && 
                       buffer[2] == 0x72 && buffer[3] == 0x74 && 
                       buffer[4] == 0x66; // {\rtf
            
            // MP4视频
            case "mp4":
                return (buffer[4] == 0x66 && buffer[5] == 0x74 && 
                        buffer[6] == 0x79 && buffer[7] == 0x70) || // ftyp
                       (buffer[0] == 0x00 && buffer[1] == 0x00 && 
                        buffer[2] == 0x00 && buffer[3] == 0x18 && 
                        buffer[4] == 0x66 && buffer[5] == 0x74 && 
                        buffer[6] == 0x79 && buffer[7] == 0x70);
            
            // AVI视频
            case "avi":
                return buffer[0] == 0x52 && buffer[1] == 0x49 && 
                       buffer[2] == 0x46 && buffer[3] == 0x46 && // RIFF
                       buffer[8] == 0x41 && buffer[9] == 0x56 && 
                       buffer[10] == 0x49 && buffer[11] == 0x20; // AVI 
            
            // MOV视频 (QuickTime)
            case "mov":
                return buffer[4] == 0x66 && buffer[5] == 0x74 && 
                       buffer[6] == 0x79 && buffer[7] == 0x70 && // ftyp
                       (buffer[8] == 0x71 && buffer[9] == 0x74 && 
                        buffer[10] == 0x20 && buffer[11] == 0x20); // qt
            
            // WMV视频
            case "wmv":
                return buffer[0] == 0x30 && buffer[1] == 0x26 && 
                       buffer[2] == (byte) 0xB2 && buffer[3] == 0x75 &&
                       buffer[4] == (byte) 0x8E && buffer[5] == 0x66 &&
                       buffer[6] == 0xCF && buffer[7] == 0x11;
            
            // FLV视频
            case "flv":
                return buffer[0] == 0x46 && buffer[1] == 0x4C && 
                       buffer[2] == 0x56 && buffer[3] == 0x01; // FLV
            
            // WebM视频
            case "webm":
                return buffer[0] == 0x1A && buffer[1] == 0x45 && 
                       buffer[2] == (byte) 0xDF && buffer[3] == (byte) 0xA3;
            
            // MKV视频
            case "mkv":
                return buffer[0] == 0x1A && buffer[1] == 0x45 && 
                       buffer[2] == (byte) 0xDF && buffer[3] == (byte) 0xA3;
            
            default:
                log.warn("未知文件类型: {}", extension);
                return false; // 未知类型拒绝
        }
    }
    
    /**
     * 检查是否为文本文件
     */
    private boolean isTextFile(byte[] buffer) {
        int textCharCount = 0;
        int totalChars = Math.min(buffer.length, 1024); // 只检查前1KB
        
        for (int i = 0; i < totalChars; i++) {
            byte b = buffer[i];
            // 检查是否为可打印字符、制表符、换行符
            if ((b >= 0x20 && b <= 0x7E) || b == 0x09 || b == 0x0A || b == 0x0D) {
                textCharCount++;
            } else if (b == 0x00) {
                // 二进制文件通常包含null字节
                return false;
            }
        }
        
        // 如果90%以上是可打印字符，认为是文本文件
        return (double) textCharCount / totalChars > 0.9;
    }
    
    /**
     * 检查文件名安全性
     */
    private boolean isSafeFilename(String filename) {
        // 检查危险字符
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }
        
        // 检查特殊字符
        if (filename.matches(".*[<>:\"|?*].*")) {
            return false;
        }
        
        // 检查文件名长度
        if (filename.length() > 255) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 按文件类型检查大小限制
     */
    private boolean checkFileSizeByType(String extension, long fileSize) {
        String lowerExt = extension.toLowerCase();
        
        if (isImageFile(lowerExt)) {
            return fileSize <= MAX_IMAGE_SIZE;
        } else if (isTextFile(lowerExt)) {
            return fileSize <= MAX_TEXT_SIZE;
        } else if (isVideoFile(lowerExt)) {
            return fileSize <= MAX_VIDEO_SIZE;
        }
        
        return fileSize <= MAX_FILE_SIZE;
    }
    
    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String extension) {
        return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg").contains(extension);
    }
    
    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String extension) {
        return Arrays.asList("txt", "md", "rtf").contains(extension);
    }
    
    /**
     * 判断是否为视频文件
     */
    private boolean isVideoFile(String extension) {
        return Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "webm", "mkv").contains(extension);
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return null;
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return null;
        }
        
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * 文件安全检查结果
     */
    public static class FileSecurityResult {
        private final boolean valid;
        private final String reason;
        
        private FileSecurityResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
        
        public static FileSecurityResult valid() {
            return new FileSecurityResult(true, null);
        }
        
        public static FileSecurityResult invalid(String reason) {
            return new FileSecurityResult(false, reason);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
