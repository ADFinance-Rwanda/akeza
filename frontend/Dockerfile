FROM node:20-alpine AS build
WORKDIR /ui
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=nginx:nginx /ui/dist /usr/share/nginx/html
EXPOSE 8080
