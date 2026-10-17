package com.apiweb.config;

import com.apiweb.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置：CORS + 认证拦截器注册。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${apiweb.security.permit-paths}")
    private List<String> permitPaths;

    /**
     * CORS 白名单（apiweb.security.cors-allowed-origins，逗号分隔）。
     * 默认为空 = 不启用跨域；企业部署建议前端同源或走 Nginx 反代。
     */
    @Value("${apiweb.security.cors-allowed-origins:}")
    private List<String> corsAllowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(permitPaths);
    }

    /**
     * CORS 仅对白名单内的来源放开（含凭证）。
     *
     * <p>不再默认 <code>allowedOriginPatterns("*") + allowCredentials(true)</code>——
     * 该组合等于对所有站点开放带凭证跨域，属高危配置；需要跨域时请通过
     * <code>CORS_ALLOWED_ORIGINS</code> 显式注入可信来源。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(corsAllowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
