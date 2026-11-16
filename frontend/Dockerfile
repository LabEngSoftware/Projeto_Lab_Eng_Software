FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# O rewrite de /api é fixado no build: o endereço do backend precisa estar definido aqui.
ARG API_URL=http://backend:8080
ENV API_URL=$API_URL NEXT_TELEMETRY_DISABLED=1
RUN npm run lint && npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
