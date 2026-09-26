package com.wyb.sseat.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.service.CommentService;
import com.wyb.sseat.service.InteractionService;
import com.wyb.sseat.service.PostService;
import com.wyb.sseat.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 论坛前台接口：帖子浏览（GET 免登录）、评论、帖子点赞收藏（需登录）。
 * 帖子详情/列表对未登录开放，符合"未登录可浏览、交互需登录"。
 */
@Tag(name="05-论坛浏览与互动")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final InteractionService interactionService;

    @GetMapping
    public Result<Page<PostVO>> page(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String type) {
        // 前台只展示审核通过的帖子
        return Result.ok(postService.page(page, size, keyword, type, null, true));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Long viewerId = CurrentUser.present() ? CurrentUser.id() : null;
        PostVO vo = postService.detail(id, viewerId, false, true);
        Map<String, Object> data = new HashMap<>();
        data.put("post", vo);
        data.put("comments", commentService.listByPost(id));
        data.put("liked", viewerId != null && interactionService.isLiked("post", id, viewerId));
        data.put("favorited", viewerId != null && interactionService.isFavorited("post", id, viewerId));
        data.put("isAuthor", viewerId != null && viewerId.equals(vo.getUserId()));
        return Result.ok(data);
    }

    /** 发表评论（需登录） */
    @PostMapping("/{id}/comments")
    public Result<Void> addComment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        commentService.add(CurrentUser.id(), id, body.get("content"));
        return Result.ok();
    }

    /** 删除评论（仅本人） */
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> delComment(@PathVariable Long commentId) {
        commentService.delete(CurrentUser.id(), false, commentId);
        return Result.ok();
    }

    /** 帖子点赞/取消（复用通用互动服务） */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        boolean liked = interactionService.toggleLike("post", id, CurrentUser.id());
        return Result.ok(Map.of("liked", liked));
    }

    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        boolean fav = interactionService.toggleFavorite("post", id, CurrentUser.id());
        return Result.ok(Map.of("favorited", fav));
    }
}
