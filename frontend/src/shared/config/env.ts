export const config = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '',
  devProxyTarget: import.meta.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080',
  actorUserId: import.meta.env.VITE_ACTOR_USER_ID || '',
  isDevelopment: import.meta.env.DEV,
} as const;
