package com.wyb.sseat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / springdoc 接口文档配置。
 * 启动后访问 http://localhost:8080/doc.html 即可看到中文接口文档，
 * 还能点"调试"直接发请求：Authorize 里填入登录返回的 token 就能测需登录接口。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sseatOpenApi() {
        // 声明"请求头里的 Bearer token"这种鉴权方式，文档页右上角 Authorize 按钮可用
        SecurityScheme jwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");
        return new OpenAPI()
                .info(new Info()
                        .title("简座 - 校园自习室预约与管理系统 API")
                        .description("简座（sseat）：基于 Spring Boot 3.5 + Vue 3 的前后端分离毕业设计项目接口文档")
                        .version("1.0.0")
                        .contact(new Contact().name("wyb")))
                .components(new Components().addSecuritySchemes("JWT", jwt))
                .addSecurityItem(new SecurityRequirement().addList("JWT"));
    }
}
