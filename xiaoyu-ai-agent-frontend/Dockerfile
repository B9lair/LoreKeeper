# =========================
# 前端构建阶段（Vite + Vue3）
# =========================
FROM node:20-alpine AS build

WORKDIR /app

# 先复制依赖清单以利用 Docker 缓存
COPY package.json package-lock.json ./
RUN npm ci

# 再复制其余源码并构建
COPY . .
RUN npm run build

# =========================
# 运行阶段（Nginx 托管静态文件 + 反代 /api）
# =========================
FROM nginx:alpine

# 复制构建产物到 nginx 静态文件目录
COPY --from=build /app/dist /usr/share/nginx/html

# 复制自定义 nginx 配置替换默认配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]

