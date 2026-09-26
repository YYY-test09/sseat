package com.wyb.sseat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统启动入口。
 * @MapperScan：告诉 MyBatis-Plus 去 mapper 包里找数据库操作接口
 * @EnableScheduling：开启定时任务（预约过期自动置"已完成"用）
 * exclude：本系统用户存数据库、由 JwtAuthenticationFilter 鉴权，
 *          排除 Spring 默认的内存用户，消除启动时的 generated password 警告
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.wyb.sseat.mapper")
@EnableScheduling
public class SseatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseatApplication.class, args);
    }

}
