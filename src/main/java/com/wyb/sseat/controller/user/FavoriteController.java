package com.wyb.sseat.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.service.InteractionService;
import com.wyb.sseat.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 前台-我的收藏管理（个人中心统一查看/取消） */
@RestController
@RequestMapping("/api/user/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final InteractionService interactionService;

    @GetMapping
    public Result<List<Map<String, Object>>> myList() {
        return Result.ok(interactionService.myFavorites(CurrentUser.id()));
    }

    /** 取消收藏：type=room/post */
    @DeleteMapping("/{type}/{targetId}")
    public Result<Void> remove(@PathVariable String type, @PathVariable Long targetId) {
        interactionService.removeFavorite(type, targetId, CurrentUser.id());
        return Result.ok();
    }
}
