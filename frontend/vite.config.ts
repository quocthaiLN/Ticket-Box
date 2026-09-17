import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3001,
    allowedHosts: [".ngrok-free.dev", ".ngrok-free.app"],
    proxy: {
      "/auth": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/oauth2/authorization": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/concerts": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/orders": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/payments": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/admin": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/uploads": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 3001,
    allowedHosts: [".ngrok-free.dev", ".ngrok-free.app"]
  }
});
