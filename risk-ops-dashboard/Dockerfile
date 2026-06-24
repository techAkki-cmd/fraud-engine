# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS builder
WORKDIR /app
ARG NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080/api/v1/stream/payments
ARG AUTH_SECRET=local-dashboard-auth-secret-change-me
ARG AUTH_URL=http://localhost:3001
ARG DASHBOARD_OIDC_CLIENT_ID=risk-ops-dashboard
ARG DASHBOARD_OIDC_CLIENT_SECRET=local-dashboard-client-secret
ARG DASHBOARD_OIDC_ISSUER=http://localhost:8089/realms/fraud-engine
ARG DASHBOARD_OIDC_ISSUER_INTERNAL=http://keycloak:8080/realms/fraud-engine
ARG DASHBOARD_OIDC_AUTHORIZATION_URL=http://localhost:8089/realms/fraud-engine/protocol/openid-connect/auth
ARG DASHBOARD_OIDC_TOKEN_URL=http://keycloak:8080/realms/fraud-engine/protocol/openid-connect/token
ARG DASHBOARD_OIDC_LOGOUT_URL=http://localhost:8089/realms/fraud-engine/protocol/openid-connect/logout
ARG DASHBOARD_POST_LOGOUT_REDIRECT_URL=http://localhost:3001/
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PUBLIC_GATEWAY_URL=${NEXT_PUBLIC_GATEWAY_URL}
ENV AUTH_SECRET=${AUTH_SECRET}
ENV AUTH_URL=${AUTH_URL}
ENV DASHBOARD_OIDC_CLIENT_ID=${DASHBOARD_OIDC_CLIENT_ID}
ENV DASHBOARD_OIDC_CLIENT_SECRET=${DASHBOARD_OIDC_CLIENT_SECRET}
ENV DASHBOARD_OIDC_ISSUER=${DASHBOARD_OIDC_ISSUER}
ENV DASHBOARD_OIDC_ISSUER_INTERNAL=${DASHBOARD_OIDC_ISSUER_INTERNAL}
ENV DASHBOARD_OIDC_AUTHORIZATION_URL=${DASHBOARD_OIDC_AUTHORIZATION_URL}
ENV DASHBOARD_OIDC_TOKEN_URL=${DASHBOARD_OIDC_TOKEN_URL}
ENV DASHBOARD_OIDC_LOGOUT_URL=${DASHBOARD_OIDC_LOGOUT_URL}
ENV DASHBOARD_POST_LOGOUT_REDIRECT_URL=${DASHBOARD_POST_LOGOUT_REDIRECT_URL}
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
RUN addgroup --system --gid 1001 nextjs \
    && adduser --system --uid 1001 nextjs \
    && rm -rf /usr/local/lib/node_modules/npm \
    && rm -rf /usr/local/lib/node_modules/corepack \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
COPY --from=builder --chown=nextjs:nextjs /app/public ./public
COPY --from=builder --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nextjs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
