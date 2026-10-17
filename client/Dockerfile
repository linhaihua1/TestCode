# 前端多阶段构建：Node 构建 -> Nginx 托管（含 /api 反代）
# 构建：docker build -t apiweb-client -f client/Dockerfile .
# 运行：docker run -p 80:80 -e API_SERVER=http://server:8080 apiweb-client

# ---------- 阶段 1：构建 ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY client/package.json client/package-lock.json ./
RUN npm ci
COPY client/ ./
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# API 反代地址通过环境变量注入（启动模板替换）
COPY client/nginx.conf /etc/nginx/templates/default.conf.template
ENV API_SERVER=http://localhost:8080
EXPOSE 80
