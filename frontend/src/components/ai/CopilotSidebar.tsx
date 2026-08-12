import { useState, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { ModelConfig } from "@/types";
import { useAuth } from "@/contexts/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Bot, Send, User, Sparkles, Loader2, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  cost?: number;
  cacheTokens?: number;
  cacheHitRate?: number;
}

interface CopilotSidebarProps {
  context?: any; // Context data (story, chapter, etc.)
  className?: string;
}

const pickDefaultChatModel = (models: ModelConfig[]): string => {
  if (!models.length) return "";
  const enabled = models.filter((m) => m.isEnabled !== false);
  const candidates = enabled.length ? enabled : models;

  const textModel = candidates.find((m) => (m.modelType || "").toLowerCase() === "text");
  if (textModel) return textModel.id;

  const nonEmbedding = candidates.find((m) => {
    const key = `${m.displayName || ""} ${m.name || ""}`.toLowerCase();
    return !key.includes("embedding") && !key.includes("ocr");
  });
  if (nonEmbedding) return nonEmbedding.id;

  return candidates[0].id;
};

const formatPercent = (value?: number): string => {
  if (value === undefined || Number.isNaN(value)) return "0%";
  return `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%`;
};

const CopilotSidebar = ({ context, className }: CopilotSidebarProps) => {
  const { user, refreshProfile } = useAuth();
  const { t } = useTranslation();
  const [messages, setMessages] = useState<Message[]>([
    { id: 'welcome', role: 'assistant', content: t('copilot.welcome') }
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [models, setModels] = useState<ModelConfig[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<string>("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api.ai
      .getModels()
      .then((data) => {
        setModels(data);
        setSelectedModelId(data.length > 0 ? pickDefaultChatModel(data) : "");
      })
      .catch(() => {
        setModels([]);
        setSelectedModelId("");
      });
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || !selectedModelId) return;
    if (user && user.credits <= 0) {
      setMessages(prev => [...prev, { id: Date.now().toString(), role: 'assistant', content: t('copilot.insufficientCredits') }]);
      return;
    }

    const userMsg: Message = { id: Date.now().toString(), role: 'user', content: input };
    setMessages(prev => [...prev, userMsg]);
    setInput("");
    setIsLoading(true);

    try {
      const response = await api.ai.chat([...messages, userMsg], selectedModelId, context);
      
      const aiMsg: Message = { 
        id: (Date.now() + 1).toString(), 
        role: 'assistant', 
        content: response.content,
        cost: response.usage?.cost,
        cacheTokens: response.usage?.cacheTokens,
        cacheHitRate: response.usage?.cacheHitRate,
      };
      
      setMessages(prev => [...prev, aiMsg]);
      await refreshProfile(); // Update credits in UI
    } catch (error) {
      setMessages(prev => [...prev, { 
        id: Date.now().toString(), 
        role: 'assistant', 
        content: t('copilot.requestFailed')
      }]);
    } finally {
      setIsLoading(false);
    }
  };

  const selectedModel = models.find(m => m.id === selectedModelId);

  return (
    <div className={cn("flex flex-col h-full border-l bg-card", className)}>
      {/* Header */}
      <div className="p-4 border-b space-y-3">
        <div className="flex items-center gap-2 font-semibold">
          <Bot className="h-5 w-5 text-primary" />
          AI Copilot
        </div>
        
        <div className="space-y-1">
          <Select value={selectedModelId} onValueChange={setSelectedModelId}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue placeholder={t("copilot.selectModel")} />
            </SelectTrigger>
            <SelectContent>
              {models.map(m => (
                <SelectItem key={m.id} value={m.id} className="text-xs">
                  <div className="flex items-center justify-between w-full gap-2">
                    <span>{m.displayName}</span>
                    <span className="text-muted-foreground opacity-70">x{m.outputMultiplier}</span>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {selectedModel && (
            <div className="text-[10px] text-muted-foreground flex justify-between px-1">
              <span>{t("copilot.inputMultiplier", { value: selectedModel.inputMultiplier })}</span>
              <span>{t("copilot.outputMultiplier", { value: selectedModel.outputMultiplier })}</span>
            </div>
          )}
        </div>
      </div>

      {/* Chat Area */}
      <ScrollArea className="flex-1 p-4" ref={scrollRef}>
        <div className="space-y-4">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={cn(
                "flex gap-3 text-sm",
                msg.role === 'user' ? "flex-row-reverse" : "flex-row"
              )}
            >
              <div className={cn(
                "h-8 w-8 rounded-full flex items-center justify-center shrink-0",
                msg.role === 'user' ? "bg-primary text-primary-foreground" : "bg-muted"
              )}>
                {msg.role === 'user' ? <User className="h-4 w-4" /> : <Sparkles className="h-4 w-4" />}
              </div>
              <div className={cn(
                "rounded-lg p-3 max-w-[85%]",
                msg.role === 'user' 
                  ? "bg-primary text-primary-foreground" 
                  : "bg-muted/50 border"
              )}>
                <div className="whitespace-pre-wrap">{msg.content}</div>
                {(msg.cost !== undefined || (msg.cacheTokens !== undefined && msg.cacheTokens > 0)) && (
                  <div className="mt-2 text-[10px] opacity-70 border-t border-border/50 pt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
                    {msg.cost !== undefined && <span>{t("copilot.cost", { cost: msg.cost })}</span>}
                    {msg.cacheTokens !== undefined && msg.cacheTokens > 0 && (
                      <span>{t("copilot.cacheHit", { tokens: Math.round(msg.cacheTokens), rate: formatPercent(msg.cacheHitRate) })}</span>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
          {isLoading && (
            <div className="flex gap-3">
              <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center shrink-0">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
              <div className="bg-muted/50 border rounded-lg p-3 text-sm text-muted-foreground">
                {t("copilot.thinking")}
              </div>
            </div>
          )}
        </div>
      </ScrollArea>

      {/* Input Area */}
      <div className="p-4 border-t bg-background">
        {user && user.credits <= 0 && (
          <div className="mb-2 flex items-center gap-2 text-xs text-destructive bg-destructive/10 p-2 rounded">
            <AlertTriangle className="h-3 w-3" />
            {t("copilot.insufficientShort")}
          </div>
        )}
        <form 
          onSubmit={(e) => { e.preventDefault(); handleSend(); }}
          className="flex gap-2"
        >
          <Input 
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={t("copilot.inputPlaceholder")}
            className="flex-1"
            disabled={isLoading || (user?.credits ?? 0) <= 0}
          />
          <Button type="submit" size="icon" disabled={isLoading || !input.trim() || (user?.credits ?? 0) <= 0}>
            <Send className="h-4 w-4" />
          </Button>
        </form>
      </div>
    </div>
  );
};

export default CopilotSidebar;
