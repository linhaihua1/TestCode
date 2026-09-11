package com.apiweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Api-Web 自动化测试平台后端入口。
 * 架构：Spring Boot 3 + MyBatis-Plus + MySQL + Redis + RabbitMQ + Quartz(集群) + MinIO。
 */
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class ApiWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiWebApplication.class, args);
    }
}
