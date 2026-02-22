/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SSO_ENTRY_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
