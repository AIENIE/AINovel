import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import { SafeErrorBoundary } from "@/components/SafeErrorBoundary";
import { installGlobalErrorHandlers } from "@/lib/client-error-reporting";
import "./globals.css";

installGlobalErrorHandlers();

createRoot(document.getElementById("root")!).render(
  <SafeErrorBoundary>
    <App />
  </SafeErrorBoundary>,
);
