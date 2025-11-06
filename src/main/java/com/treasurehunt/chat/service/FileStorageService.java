package com.treasurehunt.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储服务
 * 处理文件的上传、存储、下载链接生成等
 */
@Service
public class FileStorageService {

    @Value("${chat.file.storage-path:/data/chat/files}")
    private String storagePath;
    
    @Value("${chat.file.temp-path:/data/chat/temp}")
    private String tempPath;
    
    @Value("${chat.file.base-url:http://localhost:8080/chat/file}")
    private String baseUrl;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 存储文件到正式目录
     */
    public String storeFile(MultipartFile file, String userId) {
        try {
            // 生成文件路径
            String datePath = LocalDateTime.now().format(DATE_FORMATTER);
            String fileName = generateFileName(file.getOriginalFilename());
            Path filePath = Paths.get(storagePath, datePath, userId, fileName);
            
            // 确保目录存在
            Files.createDirectories(filePath.getParent());
            
            // 保存文件
            Files.write(filePath, file.getBytes());
            
            // 生成访问URL
            return generateFileUrl(datePath, userId, fileName);
            
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }

    /**
     * 存储文件到临时目录
     */
    public String storeTempFile(MultipartFile file, String userId) {
        try {
            // 生成临时文件路径
            String fileName = generateFileName(file.getOriginalFilename());
            Path filePath = Paths.get(tempPath, userId, fileName);
            
            // 确保目录存在
            Files.createDirectories(filePath.getParent());
            
            // 保存文件
            Files.write(filePath, file.getBytes());
            
            // 生成临时访问URL
            return generateTempFileUrl(userId, fileName);
            
        } catch (IOException e) {
            throw new RuntimeException("临时文件存储失败", e);
        }
    }

    /**
     * 将临时文件移动到正式目录
     */
    public String moveToFinalStorage(String tempFileUrl, String userId) {
        try {
            // 解析临时文件路径
            String tempFileName = extractFileNameFromUrl(tempFileUrl);
            Path tempFilePath = Paths.get(tempPath, userId, tempFileName);
            
            if (!Files.exists(tempFilePath)) {
                throw new RuntimeException("临时文件不存在");
            }
            
            // 生成正式文件路径
            String datePath = LocalDateTime.now().format(DATE_FORMATTER);
            String finalFileName = generateFileName(tempFileName);
            Path finalFilePath = Paths.get(storagePath, datePath, userId, finalFileName);
            
            // 确保目录存在
            Files.createDirectories(finalFilePath.getParent());
            
            // 移动文件
            Files.move(tempFilePath, finalFilePath);
            
            // 生成正式访问URL
            return generateFileUrl(datePath, userId, finalFileName);
            
        } catch (IOException e) {
            throw new RuntimeException("文件移动失败", e);
        }
    }

    /**
     * 删除临时文件
     */
    public void deleteTempFile(String tempFileUrl) {
        try {
            String tempFileName = extractFileNameFromUrl(tempFileUrl);
            Path tempFilePath = Paths.get(tempPath, extractUserIdFromUrl(tempFileUrl), tempFileName);
            Files.deleteIfExists(tempFilePath);
        } catch (IOException e) {
            // 记录日志但不抛出异常
            System.err.println("删除临时文件失败: " + tempFileUrl);
        }
    }

    /**
     * 生成带签名的下载链接
     */
    public String generateDownloadUrl(String fileId) {
        // 这里可以实现带签名的URL生成逻辑
        // 暂时返回简单的文件访问URL
        return baseUrl + "/download/" + fileId;
    }

    /**
     * 生成文件名
     */
    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * 生成文件访问URL
     */
    private String generateFileUrl(String datePath, String userId, String fileName) {
        return baseUrl + "/" + datePath + "/" + userId + "/" + fileName;
    }

    /**
     * 生成临时文件访问URL
     */
    private String generateTempFileUrl(String userId, String fileName) {
        return baseUrl + "/temp/" + userId + "/" + fileName;
    }

    /**
     * 从URL中提取文件名
     */
    private String extractFileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    /**
     * 从URL中提取用户ID
     */
    private String extractUserIdFromUrl(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return "unknown";
    }
}
