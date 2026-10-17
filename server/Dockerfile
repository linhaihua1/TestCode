# 后端多阶段构建：Maven 构建 -> JRE 运行
# 构建：docker build -t apiweb-server -f server/Dockerfile .
# 运行：docker run -p 8080:8080 -e JWT_SECRET=$(openssl rand -hex 32) \
#        -e BOOTSTRAP_ADMIN_PASSWORD=<强密码> \
#        -e MYSQL_HOST=<db-host> -e MYSQL_PASSWORD=<db-pass> apiweb-server

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷 pom 利用 Docker 层缓存加速依赖下载
COPY server/pom.xml .
RUN mvn -B dependency:go-offline
COPY server/src ./src
RUN mvn -B package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
# 非 root 运行
RUN useradd -r -u 1001 apiweb && chown apiweb:apiweb /app
USER apiweb
COPY --from=build /app/target/api-web-server-*.jar app.jar
EXPOSE 8080
# 生产启动：必须注入 JWT_SECRET 与 BOOTSTRAP_ADMIN_PASSWORD（ProdSecurityGuard 强制校验）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
