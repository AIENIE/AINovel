import { defineConfig } from "vite";
import dyadComponentTagger from "@dyad-sh/react-vite-component-tagger";
import react from "@vitejs/plugin-react-swc";
import path from "path";

const apiProxy = {
  "/api": {
    target: "http://127.0.0.1:11041",
    changeOrigin: true,
  },
};

export default defineConfig(() => ({
  server: {
    host: "127.0.0.1",
    port: 11040,
    strictPort: true,
    allowedHosts: ["localhost", "127.0.0.1", "localainovel.testhut.top"],
    proxy: apiProxy,
  },
  preview: {
    host: "127.0.0.1",
    port: 11040,
    strictPort: true,
    allowedHosts: ["localhost", "127.0.0.1", "localainovel.testhut.top"],
    proxy: apiProxy,
  },
  plugins: [dyadComponentTagger(), react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    setupFiles: ["src/test/setup.ts"],
    maxWorkers: 4,
  },
}));
