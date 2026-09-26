package com.wyb.sseat.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.service.InteractionService;
import com.wyb.sseat.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 前台自习室接口：浏览对未登录用户开放（Security 已放行 GET），
 * 点赞/收藏需要登录（POST 未放行）。
 */
@Tag(name="03-自习室浏览(免登录)")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final InteractionService interactionService;

    @GetMapping
    public Result<Page<StudyRoom>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "12") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String type) {
        return Result.ok(roomService.page(page, size, keyword, type, 1, false));
    }

    /** 详情：附带当前登录用户是否已点赞/收藏（未登录则为 false） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        StudyRoom room = roomService.detail(id, false);
        Map<String, Object> data = new HashMap<>();
        data.put("room", room);
        boolean logged = CurrentUser.present();
        data.put("liked", logged && interactionService.isLiked("room", id, CurrentUser.id()));
        data.put("favorited", logged && interactionService.isFavorited("room", id, CurrentUser.id()));
        return Result.ok(data);
    }

    /** 点赞/取消点赞，返回 { liked: true/false } */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        boolean liked = interactionService.toggleLike("room", id, CurrentUser.id());
        return Result.ok(Map.of("liked", liked));
    }

    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        boolean fav = interactionService.toggleFavorite("room", id, CurrentUser.id());
        return Result.ok(Map.of("favorited", fav));
    }
}
