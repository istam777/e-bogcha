/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_DEV_PROXY_TARGET: string;
  readonly VITE_ACTOR_USER_ID: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
