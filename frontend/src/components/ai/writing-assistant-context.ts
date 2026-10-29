import { createContext, useContext } from "react";
import type { useWritingAssistant } from "@/pages/Workbench/hooks/useWritingAssistant";

export const WritingAssistantContext = createContext<ReturnType<typeof useWritingAssistant> | null>(null);
export const useWritingAssistantContext = () => useContext(WritingAssistantContext);
