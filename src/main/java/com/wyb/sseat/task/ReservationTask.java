package com.wyb.sseat.task;

import com.wyb.sseat.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：把已结束的"待确认/已确认"预约自动置为"已完成"。
 * 需求描述里的"系统根据实际预约情况自动更新状态"即指此逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTask {

    private final ReservationService reservationService;

    /**
     * 每小时第 1 分钟执行一次（cron：秒 分 时 日 月 周）。
     * 例：08:00-09:00 的时段在 09 点过就应结束，9 点这一轮任务会把 8 点的记录置为已完成。
     */
    @Scheduled(cron = "0 1 * * * ?")
    public void completeExpired() {
        int n = reservationService.completeExpired();
        if (n > 0) log.info("定时任务：{} 条过期预约已置为已完成", n);
    }
}
