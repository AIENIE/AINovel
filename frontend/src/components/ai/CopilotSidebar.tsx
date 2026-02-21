import { useState, useEffect, useRef } from "react";
import { api } from "@/lib/mock-api";
import { ModelConfig } from "@/types";
import { useAuth } from "@/contexts/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Bot, Send, User, Sparkles, Loader2, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  cost?: number;
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

const CopilotSidebar = ({ context, className }: CopilotSidebarProps) => {
  const { user, refreshProfile } = useAuth();
  const [messages, setMessages] = useState<Message[]>([
    { id: 'welcome', role: 'assistant', content: '你好！我是你的 AI 写作助手。我可以帮你梳理大纲、润色段落或提供灵感。' }
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
        if (data.length > 0) setSelectedModelId(pickDefaultChatModel(data));
      })
      .catch(() => {
        setModels([
          {
            id: "fallback-model",
            name: "fallback-model",
            displayName: "本地降级模型",
            inputMultiplier: 1,
            outputMultiplier: 1,
            poolId: "fallback",
            isEnabled: true,
          },
        ]);
        setSelectedModelId("fallback-model");
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
      setMessages(prev => [...prev, { id: Date.now().toString(), role: 'assistant', content: '项目积分不足，请先在个人中心将通用积分兑换为项目积分后再继续使用 AI。' }]);
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
        cost: response.usage.cost
      };
      
      setMessages(prev => [...prev, aiMsg]);
      await refreshProfile(); // Update credits in UI
    } catch (error) {
      setMessages(prev => [...prev, { 
        id: Date.now().toString(), 
        role: 'assistant', 
        content: '抱歉，请求失败，请稍后重试。' 
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
              <SelectValue placeholder="选择模型" />
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
              <span>输入: x{selectedModel.inputMultiplier}</span>
              <span>输出: x{selectedModel.outputMultiplier}</span>
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
                {msg.cost !== undefined && (
                  <div className="mt-2 text-[10px] opacity-70 border-t border-border/50 pt-1 flex items-center gap-1">
                    <span className="font-mono">-{msg.cost}</span> 积分
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
                AI 正在思考...
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
            项目积分不足，请先兑换项目积分
          </div>
        )}
        <form 
          onSubmit={(e) => { e.preventDefault(); handleSend(); }}
          className="flex gap-2"
        >
          <Input 
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="输入指令或问题..."
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
