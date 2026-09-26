package com.wyb.sseat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.wyb.sseat.common.BizException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Web 相关配置 + 图片上传。
 * addResourceHandlers：把本地磁盘目录映射成 /uploads/** 的 URL，
 * 例如文件 D:/tmp/sseat-uploads/abc.png 可通过 http://localhost:8080/uploads/abc.png 访问。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${sseat.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);
    }

    /**
     * 上传工具方法：用 UUID 重命名防止文件同名覆盖/中文名乱码，返回访问 URL。
     * 各业务 Controller 调用它即可，不必重复写保存逻辑。
     */
    @Bean
    public UploadUtil uploadUtil() {
        return new UploadUtil(uploadDir);
    }

    public static class UploadUtil {
        private final String dir;

        public UploadUtil(String dir) {
            this.dir = dir;
        }

        public String save(MultipartFile file) {
            if (file == null || file.isEmpty()) {
                throw new BizException("请选择要上传的文件");
            }
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
            // 只允许常见图片格式，防止上传可执行文件
            if (!List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").contains(ext)) {
                throw new BizException("仅支持 jpg/png/gif/webp/bmp 图片");
            }
            try {
                Path dirPath = Path.of(dir);
                Files.createDirectories(dirPath);
                String name = UUID.randomUUID().toString().replace("-", "") + ext;
                file.transferTo(dirPath.resolve(name).toFile());
                return "/uploads/" + name;
            } catch (IOException e) {
                throw new BizException("文件保存失败：" + e.getMessage());
            }
        }
    }
}
