package com.wyb.sseat.controller.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.News;
import com.wyb.sseat.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端资讯接口：/api/admin/news/**（Security 保证仅管理员） */
@Tag(name="07-管理端-资讯")
@RestController
@RequestMapping("/api/admin/news")
@RequiredArgsConstructor
public class AdminNewsController {

    private final NewsService newsService;

    @GetMapping
    public Result<Page<News>> page(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String type) {
        return Result.ok(newsService.page(page, size, keyword, type));
    }

    /** 编辑回显：不增加浏览量 */
    @GetMapping("/{id}")
    public Result<News> detail(@PathVariable Long id) {
        return Result.ok(newsService.detail(id, false));
    }

    @PostMapping
    public Result<Void> save(@RequestBody News news) {
        newsService.save(news);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        newsService.delete(id);
        return Result.ok();
    }
}
