# Build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build

# Serve
FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/dist ./dist
RUN npm install -g serve@14 --no-audit --no-fund
EXPOSE 11040
CMD ["serve", "-s", "dist", "-l", "tcp://0.0.0.0:11040"]
