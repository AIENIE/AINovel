import { fileURLToPath } from "node:url";

const frontend = fileURLToPath(new URL("../", import.meta.url));
export default {
  root: frontend,
  resolve: { alias: [
    { find: "@", replacement: `${frontend}/src` },
    { find: /^vitest$/, replacement: `${frontend}/node_modules/vitest/dist/index.js` },
  ] },
  test: {
    environment: "jsdom",
    include: ["verification/draft-isolation.repro.test.tsx"],
    pool: "forks",
    maxWorkers: 1,
    minWorkers: 1,
  },
};
