package com.wyb.sseat.controller.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.Feedback;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.FeedbackMapper;
import com.wyb.sseat.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端反馈管理：列表 + 回复 + 删除 */
@Tag(name="07-管理端-反馈")
@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;

    @Data
    public static class ReplyReq {
        private Long id;
        private String reply;
    }

    /** 分页列表，带提交人昵称；可按"待回复/已回复"筛选 */
    @GetMapping
    public Result<Page<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String keyword) {
        Page<Feedback> raw = feedbackMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Feedback>()
                        .eq(status != null, Feedback::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), Feedback::getContent, keyword)
                        .orderByAsc(Feedback::getStatus)   // 待回复排前面
                        .orderByDesc(Feedback::getId));
        Page<Map<String, Object>> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        if (raw.getRecords().isEmpty()) { out.setRecords(List.of()); return Result.ok(out); }
        Map<Long, User> users = userMapper.selectBatchIds(
                        raw.getRecords().stream().map(Feedback::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        out.setRecords(raw.getRecords().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("content", f.getContent());
            m.put("status", f.getStatus());
            m.put("reply", f.getReply());
            m.put("replyTime", f.getReplyTime());
            m.put("createTime", f.getCreateTime());
            User u = users.get(f.getUserId());
            m.put("userLabel", u == null ? "已注销用户" : u.getUsername() + "（" + u.getNickname() + "）");
            return m;
        }).toList());
        return Result.ok(out);
    }

    /** 回复：内容同步显示在用户端，状态自动置为已回复 */
    @PutMapping("/reply")
    public Result<Void> reply(@RequestBody ReplyReq req) {
        Feedback exist = feedbackMapper.selectById(req.getId());
        if (exist == null) throw new BizException("反馈不存在");
        if (req.getReply() == null || req.getReply().isBlank()) throw new BizException("回复内容不能为空");
        Feedback f = new Feedback();
        f.setId(req.getId());
        f.setReply(req.getReply().trim());
        f.setStatus(1);
        f.setReplyTime(LocalDateTime.now());
        feedbackMapper.updateById(f);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        feedbackMapper.deleteById(id);
        return Result.ok();
    }
}
