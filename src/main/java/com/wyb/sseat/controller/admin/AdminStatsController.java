package com.wyb.sseat.controller.admin;

import com.wyb.sseat.common.Result;
import com.wyb.sseat.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端-统计仪表盘（仅 ROLE_admin，Security 全局规则已拦） */
@Tag(name = "08-管理端-统计")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @Operation(summary = "仪表盘汇总数据（卡片+趋势+排行+分布）")
    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(adminStatsService.dashboard());
    }
}
