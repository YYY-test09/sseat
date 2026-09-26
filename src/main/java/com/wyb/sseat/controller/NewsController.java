package com.wyb.sseat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.News;
import com.wyb.sseat.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 前台资讯接口（GET 免登录，Security 已放行 /api/news/**） */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public Result<Page<News>> page(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String type) {
        return Result.ok(newsService.page(page, size, keyword, type));
    }

    /** 详情：浏览量 +1 */
    @GetMapping("/{id}")
    public Result<News> detail(@PathVariable Long id) {
        return Result.ok(newsService.detail(id, true));
    }
}
