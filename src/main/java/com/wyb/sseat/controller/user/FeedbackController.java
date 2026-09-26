package com.wyb.sseat.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.Feedback;
import com.wyb.sseat.mapper.FeedbackMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** 用户端意见反馈：提交 + 查看自己的反馈与管理员回复 */
@RestController
@RequestMapping("/api/user/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackMapper feedbackMapper;

    @Data
    public static class SubmitReq {
        private String content;
    }

    @PostMapping
    public Result<Void> submit(@RequestBody SubmitReq req) {
        if (req.getContent() == null || req.getContent().isBlank()) throw new BizException("反馈内容不能为空");
        if (req.getContent().length() > 1000) throw new BizException("反馈内容不能超过1000字");
        Feedback f = new Feedback();
        f.setUserId(CurrentUser.id());
        f.setContent(req.getContent().trim());
        f.setStatus(0);
        feedbackMapper.insert(f);
        return Result.ok();
    }

    /** 我的反馈列表（含管理员回复与回复时间） */
    @GetMapping("/mine")
    public Result<Page<Feedback>> mine(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size) {
        return Result.ok(feedbackMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, CurrentUser.id())
                        .orderByDesc(Feedback::getId)));
    }
}
