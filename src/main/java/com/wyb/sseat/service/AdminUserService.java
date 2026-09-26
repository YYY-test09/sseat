package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.common.Sha256Util;
import com.wyb.sseat.dto.UserDto;
import com.wyb.sseat.entity.*;
import com.wyb.sseat.mapper.*;
import com.wyb.sseat.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端：用户信息管理。
 * 保护规则：不能禁用/删除/重置"自己"，内置 admin 账号不可删除。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ReservationMapper reservationMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final FeedbackMapper feedbackMapper;
    private final FavoriteMapper favoriteMapper;
    private final LikeRecordMapper likeRecordMapper;

    /** 分页查询用户列表，支持按账号/昵称关键字、角色、状态筛选 */
    public Page<UserVO> page(long pageNo, long size, String keyword, String roleCode, Integer status) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<User>()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword))
                .eq(roleCode != null && !roleCode.isBlank(), User::getRoleCode, roleCode)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getId);
        Page<User> raw = userMapper.selectPage(new Page<>(pageNo, size), q);

        // 批量查角色用于填充 roleName/menus，避免每行一次查询
        Map<String, Role> roleMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getRoleCode, Function.identity()));

        Page<UserVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream()
                .map(u -> UserVO.of(u, roleMap.get(u.getRoleCode())))
                .toList());
        return result;
    }

    public void create(UserDto.CreateReq req, Long currentUserId) {
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()))) {
            throw new BizException("该账号已存在");
        }
        checkRoleExists(req.getRoleCode());
        checkPhoneAvailable(req.getPhone(), null);
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(Sha256Util.hash(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank() ? req.getUsername() : req.getNickname());
        user.setPhone(emptyToNull(req.getPhone()));
        user.setEmail(req.getEmail());
        user.setGender(req.getGender() == null ? 0 : req.getGender());
        user.setRoleCode(req.getRoleCode());
        user.setStatus(1);
        userMapper.insert(user);
    }

    public void update(UserDto.UpdateReq req) {
        User exist = userMapper.selectById(req.getId());
        if (exist == null) throw new BizException("用户不存在");
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()) {
            checkRoleExists(req.getRoleCode());
            if ("admin".equals(exist.getUsername()) && !"admin".equals(req.getRoleCode())) {
                throw new BizException("不能修改内置管理员的角色");
            }
        }
        checkPhoneAvailable(req.getPhone(), req.getId());
        User u = new User();
        u.setId(req.getId());
        u.setNickname(req.getNickname());
        u.setPhone(emptyToNull(req.getPhone()));
        u.setEmail(req.getEmail());
        u.setGender(req.getGender());
        u.setAddress(req.getAddress());
        u.setIntro(req.getIntro());
        u.setAvatar(req.getAvatar());
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()) u.setRoleCode(req.getRoleCode());
        userMapper.updateById(u);
    }

    public void changeStatus(Long id, Integer status, Long currentUserId) {
        if (id.equals(currentUserId)) throw new BizException("不能禁用自己的账号");
        User exist = mustExist(id);
        if ("admin".equals(exist.getUsername()) && status == 0) {
            throw new BizException("不能禁用内置管理员账号");
        }
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        userMapper.updateById(u);
        // 禁用后该用户的旧令牌在下一次请求时即失效（过滤器查库核对 status）
    }

    /** 重置密码为 123456，需求文档中的常见做法 */
    public void resetPassword(Long id) {
        mustExist(id);
        User u = new User();
        u.setId(id);
        u.setPassword(Sha256Util.hash("123456"));
        userMapper.updateById(u);
    }

    /** 删除用户并同步清理其预约、帖子、评论、反馈、收藏、点赞（需求明确要求） */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        User exist = mustExist(id);
        if (id.equals(currentUserId)) throw new BizException("不能删除自己的账号");
        if ("admin".equals(exist.getUsername())) throw new BizException("内置管理员账号不可删除");
        reservationMapper.delete(new LambdaQueryWrapper<Reservation>().eq(Reservation::getUserId, id));
        postMapper.delete(new LambdaQueryWrapper<Post>().eq(Post::getUserId, id));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, id));
        feedbackMapper.delete(new LambdaQueryWrapper<Feedback>().eq(Feedback::getUserId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, id));
        likeRecordMapper.delete(new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getUserId, id));
        userMapper.deleteById(id);
    }

    // ---------- 私有校验 ----------

    private User mustExist(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        return u;
    }

    private void checkRoleExists(String roleCode) {
        if (!roleMapper.exists(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleCode))) {
            throw new BizException("角色不存在：" + roleCode);
        }
    }

    private void checkPhoneAvailable(String phone, Long excludeId) {
        if (phone == null || phone.isBlank()) return;
        boolean used = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone).ne(excludeId != null, User::getId, excludeId));
        if (used) throw new BizException("该手机号已被其他账号绑定");
    }

    /** 空串写库会撞 phone 唯一索引（''只允许一条），统一转 null */
    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
