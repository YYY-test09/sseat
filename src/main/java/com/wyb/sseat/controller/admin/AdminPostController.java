package com.wyb.sseat.controller.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.Post;
import com.wyb.sseat.service.PostService;
import com.wyb.sseat.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端论坛：审核（通过/驳回带原因）、编辑、删除、按状态筛选 */
@Tag(name="07-管理端-论坛审核")
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    public Result<Page<PostVO>> page(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(required = false) Integer status) {
        return Result.ok(postService.adminPage(page, size, keyword, type, status));
    }

    @GetMapping("/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(id, null, true, false));
    }

    /** 审核：{ status: 1通过 | 2驳回, rejectReason } */
    @PutMapping("/audit")
    public Result<Void> audit(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        Integer status = Integer.valueOf(body.get("status").toString());
        String reason = body.get("rejectReason") == null ? "" : body.get("rejectReason").toString();
        postService.audit(id, status, reason);
        return Result.ok();
    }

    @PostMapping
    public Result<Void> save(@RequestBody Post form) {
        postService.adminSave(form);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.adminDelete(id);
        return Result.ok();
    }
}
