import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite = the dev server + build tool (fast, ES-module based). @vitejs/plugin-react
// enables JSX + Fast Refresh. The `test` block configures Vitest to run our
// component tests in a simulated browser (jsdom) — so tests run headless in Node.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,              // describe/it/expect available without imports
    environment: 'jsdom',       // a fake DOM so React can render in Node
    setupFiles: './src/test/setup.js',
  },
});
