package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.entity.Favorite;
import com.wyb.sseat.entity.LikeRecord;
import com.wyb.sseat.entity.Post;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.mapper.FavoriteMapper;
import com.wyb.sseat.mapper.LikeRecordMapper;
import com.wyb.sseat.mapper.PostMapper;
import com.wyb.sseat.mapper.StudyRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 点赞 / 收藏通用服务（自习室与帖子共用）。
 * 规则（与需求一致）：每人每内容只能点赞一次，再次点击取消；收藏同理。
 * 点赞数冗余在目标表 like_count 字段上，点赞/取消时同步 +1/-1。
 */
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final LikeRecordMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final StudyRoomMapper roomMapper;
    private final PostMapper postMapper;

    /** 切换点赞，返回操作后是否为"已点赞"状态 */
    public boolean toggleLike(String targetType, Long targetId, Long userId) {
        checkType(targetType);
        LikeRecord exist = likeMapper.selectOne(queryLike(userId, targetType, targetId));
        if (exist != null) {
            likeMapper.deleteById(exist.getId());
            updateCount(targetType, targetId, -1);
            return false;
        }
        LikeRecord rec = new LikeRecord();
        rec.setUserId(userId);
        rec.setTargetType(targetType);
        rec.setTargetId(targetId);
        likeMapper.insert(rec);
        updateCount(targetType, targetId, 1);
        return true;
    }

    /** 切换收藏，返回操作后是否为"已收藏"状态 */
    public boolean toggleFavorite(String targetType, Long targetId, Long userId) {
        checkType(targetType);
        checkTargetExists(targetType, targetId);
        Favorite exist = favoriteMapper.selectOne(queryFav(userId, targetType, targetId));
        if (exist != null) {
            favoriteMapper.deleteById(exist.getId());
            return false;
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setTargetType(targetType);
        fav.setTargetId(targetId);
        favoriteMapper.insert(fav);
        return true;
    }

    /** 当前用户是否点赞过（详情页按钮回显） */
    public boolean isLiked(String targetType, Long targetId, Long userId) {
        return likeMapper.exists(queryLike(userId, targetType, targetId));
    }

    public boolean isFavorited(String targetType, Long targetId, Long userId) {
        return favoriteMapper.exists(queryFav(userId, targetType, targetId));
    }

    /** 我的收藏列表：按类型批量取出自习室/帖子摘要，组装后返回 */
    public List<Map<String, Object>> myFavorites(Long userId) {
        List<Favorite> favs = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId).orderByDesc(Favorite::getId));
        if (favs.isEmpty()) return List.of();

        List<Long> roomIds = favs.stream().filter(f -> "room".equals(f.getTargetType())).map(Favorite::getTargetId).toList();
        List<Long> postIds = favs.stream().filter(f -> "post".equals(f.getTargetType())).map(Favorite::getTargetId).toList();
        Map<Long, StudyRoom> roomMap = roomIds.isEmpty() ? Map.of()
                : roomMapper.selectBatchIds(roomIds).stream().collect(Collectors.toMap(StudyRoom::getId, r -> r));
        Map<Long, Post> postMap = postIds.isEmpty() ? Map.of()
                : postMapper.selectBatchIds(postIds).stream().collect(Collectors.toMap(Post::getId, p -> p));

        return favs.stream().map(f -> {
            Map<String, Object> item = new HashMap<>();
            item.put("targetType", f.getTargetType());
            item.put("targetId", f.getTargetId());
            item.put("favoriteTime", f.getCreateTime());
            if ("room".equals(f.getTargetType())) {
                item.put("room", roomMap.get(f.getTargetId())); // null=原内容已被删除
            } else {
                Post p = postMap.get(f.getTargetId());
                if (p != null) { // 只回显必要字段
                    Map<String, Object> brief = new HashMap<>();
                    brief.put("id", p.getId());
                    brief.put("title", p.getTitle());
                    brief.put("type", p.getType());
                    brief.put("status", p.getStatus());
                    item.put("post", brief);
                }
            }
            return item;
        }).toList();
    }

    /** 取消收藏（不依赖目标是否仍存在） */
    public void removeFavorite(String targetType, Long targetId, Long userId) {
        favoriteMapper.delete(queryFav(userId, targetType, targetId));
    }

    // ---------- 私有 ----------

    private LambdaQueryWrapper<LikeRecord> queryLike(Long userId, String type, Long id) {
        return new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId).eq(LikeRecord::getTargetType, type).eq(LikeRecord::getTargetId, id);
    }

    private LambdaQueryWrapper<Favorite> queryFav(Long userId, String type, Long id) {
        return new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getTargetType, type).eq(Favorite::getTargetId, id);
    }

    private void checkType(String targetType) {
        if (!"room".equals(targetType) && !"post".equals(targetType)) {
            throw new BizException("不支持的点赞/收藏类型");
        }
    }

    private void checkTargetExists(String targetType, Long targetId) {
        boolean exists = "room".equals(targetType)
                ? roomMapper.selectById(targetId) != null
                : postMapper.selectById(targetId) != null;
        if (!exists) throw new BizException("内容不存在或已被删除");
    }

    /** 同步目标表的 like_count：用 UpdateWrapper 做原子加减，防止并发覆盖 */
    private void updateCount(String targetType, Long targetId, int delta) {
        if ("room".equals(targetType)) {
            roomMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<StudyRoom>lambdaUpdate()
                    .setSql("like_count = GREATEST(like_count + (" + delta + "), 0)")
                    .eq(StudyRoom::getId, targetId));
        } else {
            postMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Post>lambdaUpdate()
                    .setSql("like_count = GREATEST(like_count + (" + delta + "), 0)")
                    .eq(Post::getId, targetId));
        }
    }
}
