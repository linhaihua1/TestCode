package com.apiweb.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（springdoc）文档配置。
 *
 * <p>提供：
 * <ul>
 *   <li>文档标题 / 版本 / 描述 / 联系方式 / 许可证</li>
 *   <li>Bearer JWT 安全方案（Swagger UI 右上角 Authorize 可填入 token 后调试受保护接口）</li>
 * </ul>
 *
 * <p>文档入口（已纳入认证白名单，免登录访问）：
 * <ul>
 *   <li>Swagger UI：{@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON：{@code /v3/api-docs}</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** JWT 安全方案名称（对应请求头 {@code Authorization: Bearer <token>}） */
    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI apiWebOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Api-Web 自动化测试平台 API")
                        .description("接口自动化 + UI 自动化 + 性能测试一体化测试平台后端接口文档。"
                                + "受保护接口需先在右上角 Authorize 填入登录返回的 JWT token。")
                        .version("0.2.0")
                        .contact(new Contact().name("Api-Web Team"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
