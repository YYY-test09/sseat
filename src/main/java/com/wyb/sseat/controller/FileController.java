package com.wyb.sseat.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.config.WebMvcConfig.UploadUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 通用图片上传接口：登录后可用，返回图片访问 URL */
@Tag(name="02-文件上传")
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class FileController {

    private final UploadUtil uploadUtil;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(uploadUtil.save(file));
    }
}
