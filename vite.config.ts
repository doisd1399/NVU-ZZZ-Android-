import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";

export default defineConfig(() => {
  const injectedPort = Number.parseInt(process.env.PORT || "", 10);
  const runtimePort =
    Number.isFinite(injectedPort) && injectedPort > 0 ? injectedPort : 3000;

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "."),
      },
    },
    optimizeDeps: {
      include: [
        "react",
        "react-dom/client",
        "react-router-dom",
        "firebase/app",
        "firebase/auth",
        "firebase/firestore",
        "firebase/storage",
        "lucide-react",
      ],
    },
    build: {
      outDir: "dist",
      target: "es2022",
      minify: "esbuild",
      sourcemap: false,
      reportCompressedSize: true,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes("node_modules")) {
              if (id.includes("lucide-react")) return "vendor-icons";
              if (id.includes("tesseract.js")) return "vendor-tesseract";
              if (id.includes("motion") || id.includes("framer-motion")) return "vendor-motion";
              return "vendor";
            }
          },
        },
      },
    },
    server: {
      host: "0.0.0.0",
      port: runtimePort,
      warmup: {
        clientFiles: [
          "./src/main.tsx",
          "./src/App.tsx",
          "./src/pages/Portal.tsx",
          "./src/pages/Login.tsx",
          "./src/pages/SelectProfile.tsx",
        ],
      },
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== "true",
    },
    preview: {
      host: "0.0.0.0",
      port: runtimePort,
    },
  };
});
