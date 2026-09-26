package com.wyb.sseat.controller.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.Post;
import com.wyb.sseat.service.PostService;
import com.wyb.sseat.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 用户端论坛：发帖、我的帖子（含审核状态）、编辑重审、删除 */
@Tag(name="06-用户端-我的帖子")
@RestController
@RequestMapping("/api/user/posts")
@RequiredArgsConstructor
public class MyPostController {

    private final PostService postService;

    /** 我的帖子列表（全部状态，含待审/驳回及原因） */
    @GetMapping("/mine")
    public Result<Page<PostVO>> mine(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size) {
        return Result.ok(postService.page(page, size, null, null, CurrentUser.id(), false));
    }

    /** 发帖：提交后进入待审核 */
    @PostMapping
    public Result<Long> publish(@RequestBody Post form) {
        return Result.ok(postService.publish(CurrentUser.id(), form));
    }

    /** 编辑自己的帖子：会重新进入待审核 */
    @PutMapping
    public Result<Void> update(@RequestBody Post form) {
        postService.updateOwn(CurrentUser.id(), form);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deleteOwn(CurrentUser.id(), id);
        return Result.ok();
    }
}
