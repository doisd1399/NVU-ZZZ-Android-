import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./index.css";
import { installDeployRecovery } from "./lib/deployRecovery";
import { prepareWebRuntime } from "./lib/webRuntimeRecovery";
import { AppErrorBoundary } from "./components/common/AppErrorBoundary";

prepareWebRuntime();
installDeployRecovery();

// StrictMode intentionally omitted for this operational SPA. In AI Studio/dev
// React mounts effects twice, which duplicated Firestore listeners, preloads and
// image state transitions and made navigation look slower than production.
// Regression coverage is handled by the project tests instead.
const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("Elemento raiz do NVU não encontrado.");
}

createRoot(rootElement).render(
  <AppErrorBoundary>
    <App />
  </AppErrorBoundary>,
);
