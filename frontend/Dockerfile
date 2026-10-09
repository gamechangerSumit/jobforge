# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# Only NEXT_PUBLIC_* values are baked in; BACKEND_INTERNAL_URL is read by the rewrite at build time.
ARG BACKEND_INTERNAL_URL=http://backend:8080
ENV BACKEND_INTERNAL_URL=$BACKEND_INTERNAL_URL
RUN pnpm build

FROM node:22-alpine
WORKDIR /app
RUN corepack enable && addgroup -S jobforge && adduser -S jobforge -G jobforge
COPY --from=build /app ./
ENV NODE_ENV=production
EXPOSE 3000
USER jobforge
CMD ["pnpm", "start"]
