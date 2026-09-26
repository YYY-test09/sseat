package com.wyb.sseat.task;

import com.wyb.sseat.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：预约状态自动巡检（需求描述里的"系统根据实际预约情况自动更新状态"）。
 * 规则一：时段开始 15 分钟后仍未签到 → 违约取消、释放座位；
 * 规则二：已签到且整个时段结束 → 置为已完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTask {

    private final ReservationService reservationService;

    /**
     * 每 5 分钟执行一次（cron 六段：秒 分 时 日 月 周；分的 "0/5" 表示每5分钟）。
     * 频率比"签到窗口15分钟"快即可保证违约及时释放，也不给数据库压力。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void refreshReservationStatus() {
        int n = reservationService.completeExpired();
        if (n > 0) log.info("定时任务：{} 条预约状态已自动更新（违约取消/置已完成）", n);
    }
}
