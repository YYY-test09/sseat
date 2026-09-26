package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.entity.Comment;
import com.wyb.sseat.entity.Post;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.CommentMapper;
import com.wyb.sseat.mapper.PostMapper;
import com.wyb.sseat.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 帖子评论业务（评论不设审核，直接可见，属毕设范围取舍） */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    /** 某帖评论列表（按时间正序，带评论人昵称头像） */
    public List<Map<String, Object>> listByPost(Long postId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, postId).orderByAsc(Comment::getId));
        if (comments.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(
                        comments.stream().map(Comment::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return comments.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("createTime", c.getCreateTime());
            User u = users.get(c.getUserId());
            m.put("authorName", u == null ? "已注销用户" : u.getNickname());
            m.put("authorAvatar", u == null ? "" : u.getAvatar());
            m.put("isOwn", false);
            return m;
        }).toList();
    }

    public void add(Long userId, Long postId, String content) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != 1) throw new BizException("帖子不存在或未通过审核");
        if (content == null || content.isBlank()) throw new BizException("评论内容不能为空");
        if (content.length() > 500) throw new BizException("评论不能超过500字");
        Comment c = new Comment();
        c.setUserId(userId);
        c.setPostId(postId);
        c.setContent(content.trim());
        commentMapper.insert(c);
    }

    /** 删除评论：仅评论人或管理员可删 */
    public void delete(Long userId, boolean isAdmin, Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) throw new BizException("评论不存在");
        if (!isAdmin && !c.getUserId().equals(userId)) throw new BizException("只能删除自己的评论");
        commentMapper.deleteById(id);
    }
}
