FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# 127.0.0.1 explicite (pas "localhost") : nginx n'ecoute qu'en IPv4
# (0.0.0.0:80) et "localhost" resout d'abord en IPv6 (::1) dans ce conteneur --
# le check echouait donc systematiquement (unhealthy) alors que nginx
# repondait normalement, comme confirme par wget http://127.0.0.1/ en direct.
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1
