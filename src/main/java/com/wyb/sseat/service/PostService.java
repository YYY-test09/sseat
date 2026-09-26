package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.entity.Comment;
import com.wyb.sseat.entity.Post;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.CommentMapper;
import com.wyb.sseat.mapper.PostMapper;
import com.wyb.sseat.mapper.UserMapper;
import com.wyb.sseat.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 论坛业务：发帖(待审核)、前台只看已通过、我的帖子(含各状态)、管理员审核、删除级联评论。
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;

    /**
     * 分页查询帖子。
     * @param userIdForMine 不为空=查"我的帖子"(该用户全部状态)；为空时按 visibleOnly 决定
     * @param visibleOnly true=只返回审核通过(status=1)的前台列表
     */
    public Page<PostVO> page(long pageNo, long size, String keyword, String type,
                             Long userIdForMine, boolean visibleOnly) {
        LambdaQueryWrapper<Post> q = new LambdaQueryWrapper<Post>()
                .like(keyword != null && !keyword.isBlank(), Post::getTitle, keyword)
                .eq(type != null && !type.isBlank(), Post::getType, type)
                .eq(userIdForMine != null, Post::getUserId, userIdForMine)
                .eq(visibleOnly && userIdForMine == null, Post::getStatus, 1)
                .orderByDesc(Post::getId);
        return toVOPage(postMapper.selectPage(new Page<>(pageNo, size), q));
    }

    /** 详情。前台可见性规则：已通过的任何人都能看；未通过仅作者本人或管理员可看（admin 由调用方判断） */
    public PostVO detail(Long id, Long viewerId, boolean isAdmin, boolean countView) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new BizException("帖子不存在");
        boolean isAuthor = viewerId != null && viewerId.equals(post.getUserId());
        if (post.getStatus() != 1 && !isAuthor && !isAdmin) {
            throw new BizException("该帖子未通过审核或已下架");
        }
        if (countView) {
            postMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Post>lambdaUpdate()
                    .setSql("view_count = view_count + 1").eq(Post::getId, id));
            post.setViewCount(post.getViewCount() + 1); // 让本次返回的浏览量就是+1后的值
        }
        return toVO(post);
    }

    /** 用户发帖：提交后状态=0待审核 */
    public Long publish(Long userId, Post form) {
        if (form.getTitle() == null || form.getTitle().isBlank()) throw new BizException("请输入标题");
        if (form.getContent() == null || form.getContent().isBlank()) throw new BizException("请输入正文");
        if (form.getType() == null || form.getType().isBlank()) throw new BizException("请选择帖子类型");
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(form.getTitle());
        post.setType(form.getType());
        post.setContent(form.getContent());
        post.setCoverImg(form.getCoverImg());
        post.setStatus(0);
        postMapper.insert(post);
        return post.getId();
    }

    /** 我的帖子：仅作者可改标题/正文/封面（重新提交进入待审核），或删除 */
    public void updateOwn(Long userId, Post form) {
        Post exist = mustOwn(userId, form.getId());
        Post p = new Post();
        p.setId(exist.getId());
        p.setTitle(form.getTitle());
        p.setType(form.getType());
        p.setContent(form.getContent());
        p.setCoverImg(form.getCoverImg());
        p.setStatus(0); // 修改后重新审核
        p.setRejectReason(""); // 清空旧驳回原因
        postMapper.updateById(p);
    }

    /** 删除自己的帖子：连带删除其评论 */
    @Transactional
    public void deleteOwn(Long userId, Long id) {
        mustOwn(userId, id);
        deleteInternal(id);
    }

    private void deleteInternal(Long id) {
        postMapper.deleteById(id);
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, id));
    }

    // ---------- 管理端 ----------

    /** 管理端审核列表：可按状态筛选（0待审 1通过 2驳回 全部=null） */
    public Page<PostVO> adminPage(long pageNo, long size, String keyword, String type, Integer status) {
        LambdaQueryWrapper<Post> q = new LambdaQueryWrapper<Post>()
                .like(keyword != null && !keyword.isBlank(), Post::getTitle, keyword)
                .eq(type != null && !type.isBlank(), Post::getType, type)
                .eq(status != null, Post::getStatus, status)
                .orderByAsc(Post::getStatus).orderByDesc(Post::getId); // 待审优先展示
        return toVOPage(postMapper.selectPage(new Page<>(pageNo, size), q));
    }

    /** 审核：status=1通过 / 2驳回(带原因)；管理员也可直接新增置顶公告帖 */
    public void audit(Long id, Integer status, String rejectReason) {
        Post exist = postMapper.selectById(id);
        if (exist == null) throw new BizException("帖子不存在");
        if (status != 1 && status != 2) throw new BizException("审核状态不合法");
        Post p = new Post();
        p.setId(id);
        p.setStatus(status);
        p.setRejectReason(status == 2 ? (rejectReason == null ? "" : rejectReason) : "");
        postMapper.updateById(p);
    }

    /** 管理员编辑帖子内容 */
    public void adminSave(Post form) {
        if (form.getId() != null && postMapper.selectById(form.getId()) != null) {
            postMapper.updateById(form);
        } else {
            throw new BizException("帖子不存在");
        }
    }

    /** 管理员删除任意帖子（连带评论） */
    @Transactional
    public void adminDelete(Long id) {
        deleteInternal(id);
    }

    // ---------- 私有 ----------

    private Post mustOwn(Long userId, Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new BizException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new BizException("只能操作自己发布的帖子");
        return post;
    }

    private PostVO toVO(Post p) {
        PostVO v = new PostVO();
        copyInto(p, v);
        User author = userMapper.selectById(p.getUserId());
        if (author != null) { v.setAuthorName(author.getNickname()); v.setAuthorAvatar(author.getAvatar()); }
        return v;
    }

    private void copyInto(Post p, PostVO v) {
        v.setId(p.getId()); v.setUserId(p.getUserId()); v.setTitle(p.getTitle());
        v.setType(p.getType()); v.setCoverImg(p.getCoverImg()); v.setContent(p.getContent());
        v.setStatus(p.getStatus()); v.setRejectReason(p.getRejectReason());
        v.setViewCount(p.getViewCount());
        v.setLikeCount(p.getLikeCount()); v.setCreateTime(p.getCreateTime());
    }

    /** 批量补作者信息，避免 N+1 */
    private Page<PostVO> toVOPage(Page<Post> raw) {
        Page<PostVO> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<Post> records = raw.getRecords();
        if (records.isEmpty()) { out.setRecords(List.of()); return out; }
        Map<Long, User> userMap = userMapper.selectBatchIds(
                        records.stream().map(Post::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        out.setRecords(records.stream().map(p -> {
            PostVO v = new PostVO();
            copyInto(p, v);
            User a = userMap.get(p.getUserId());
            if (a != null) { v.setAuthorName(a.getNickname()); v.setAuthorAvatar(a.getAvatar()); }
            return v;
        }).toList());
        return out;
    }
}
