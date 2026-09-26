package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wyb.sseat.entity.Feedback;
import com.wyb.sseat.entity.Post;
import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.mapper.FeedbackMapper;
import com.wyb.sseat.mapper.PostMapper;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.mapper.StudyRoomMapper;
import com.wyb.sseat.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端统计仪表盘数据：一次请求返回全部图表所需数据（毕设数据量小，无需缓存）。
 * 分组统计用 QueryWrapper 的 select("... group by") —— 这是 MyBatis-Plus 写聚合查询的标准姿势。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserMapper userMapper;
    private final StudyRoomMapper roomMapper;
    private final ReservationMapper reservationMapper;
    private final PostMapper postMapper;
    private final FeedbackMapper feedbackMapper;

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new HashMap<>();

        // ---------- 顶部数字卡片 ----------
        Map<String, Object> cards = new HashMap<>();
        cards.put("userCount", userMapper.selectCount(null));
        cards.put("roomCount", roomMapper.selectCount(new QueryWrapper<StudyRoom>().eq("status", 1)));
        cards.put("reservationCount", reservationMapper.selectCount(null));
        cards.put("activeCount", reservationMapper.selectCount(
                new QueryWrapper<Reservation>().in("status", 0, 1)));
        cards.put("todayCount", reservationMapper.selectCount(
                new QueryWrapper<Reservation>().eq("reserve_date", LocalDate.now()).in("status", 0, 1)));
        cards.put("postCount", postMapper.selectCount(new QueryWrapper<Post>().eq("status", 1)));
        cards.put("pendingPosts", postMapper.selectCount(new QueryWrapper<Post>().eq("status", 0)));
        cards.put("pendingFeedbacks", feedbackMapper.selectCount(
                new QueryWrapper<Feedback>().eq("status", 0)));
        data.put("cards", cards);

        // 统计窗口取"今天起7天"：预约都是约未来的，取过去7天图表会全空，演示效果差
        LocalDate since = LocalDate.now();

        // ---------- 未来7天预约日期分布（按预约日期分组，缺失日期补0） ----------
        Map<String, Long> byDate = toCountMap(reservationMapper.selectMaps(
                new QueryWrapper<Reservation>()
                        .select("reserve_date AS k", "count(*) AS c")
                        .ge("reserve_date", since)
                        .in("status", 0, 1, 2)
                        .groupBy("reserve_date")), "k");
        List<String> days = new ArrayList<>();
        List<Long> dayCounts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = since.plusDays(i);
            days.add(d.toString().substring(5)); // MM-dd 作为 X 轴
            dayCounts.add(byDate.getOrDefault(d.toString(), 0L));
        }
        data.put("trendDays", days);
        data.put("trendCounts", dayCounts);

        // ---------- 各自习室预约排行（今日起7天内，倒序） ----------
        Map<String, Long> byRoom = toCountMap(reservationMapper.selectMaps(
                new QueryWrapper<Reservation>()
                        .select("room_id AS k", "count(*) AS c")
                        .ge("reserve_date", since)
                        .in("status", 0, 1, 2)
                        .groupBy("room_id")), "k");
        Map<Long, String> roomNames = roomMapper.selectList(null).stream()
                .collect(Collectors.toMap(StudyRoom::getId, StudyRoom::getName));
        List<Map<String, Object>> roomRank = byRoom.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", roomNames.getOrDefault(Long.valueOf(e.getKey()), "已删除自习室"));
                    m.put("count", e.getValue());
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .limit(8).toList();
        data.put("roomRank", roomRank);

        // ---------- 预约状态分布（全量，饼图） ----------
        Map<String, Long> byStatus = toCountMap(reservationMapper.selectMaps(
                new QueryWrapper<Reservation>().select("status AS k", "count(*) AS c").groupBy("status")), "k");
        String[] statusNames = {"待确认", "已确认", "已完成", "已取消"};
        List<Map<String, Object>> statusDist = new ArrayList<>();
        for (int s = 0; s < statusNames.length; s++) {
            long c = byStatus.getOrDefault(String.valueOf(s), 0L);
            if (c > 0) { // 数量为0的扇区不展示，图更干净
                Map<String, Object> m = new HashMap<>();
                m.put("name", statusNames[s]);
                m.put("value", c);
                statusDist.add(m);
            }
        }
        data.put("statusDist", statusDist);

        // ---------- 帖子类型分布（论坛活跃度参考） ----------
        Map<String, Long> byType = toCountMap(postMapper.selectMaps(
                new QueryWrapper<Post>().select("type AS k", "count(*) AS c").eq("status", 1).groupBy("type")), "k");
        data.put("postTypes", byType);

        return data;
    }

    /** selectMaps 返回 [{k=..., c=...}]，转成 key→count 的 Map（键统一按字符串处理） */
    private Map<String, Long> toCountMap(List<Map<String, Object>> rows, String keyName) {
        Map<String, Long> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            out.put(String.valueOf(row.get(keyName)), ((Number) row.get("c")).longValue());
        }
        return out;
    }
}
