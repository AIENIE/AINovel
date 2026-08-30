import type { NetworkObject } from "@/lib/api-client";
import { useTranslation } from "react-i18next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TabsContent } from "@/components/ui/tabs";

type GoalsSidebarPanelProps = {
  createGoal: () => Promise<void> | void;
  deleteGoal: (goalId: string) => Promise<void> | void;
  goalTargetValue: number;
  goalType: string;
  goals: NetworkObject[];
  setGoalTargetValue: (value: number) => void;
  setGoalType: (value: string) => void;
  updateGoal: (goalId: string, patch: Record<string, unknown>) => Promise<void> | void;
};

export function GoalsSidebarPanel({
  createGoal,
  deleteGoal,
  goalTargetValue,
  goalType,
  goals,
  setGoalTargetValue,
  setGoalType,
  updateGoal,
}: GoalsSidebarPanelProps) {
  const { t } = useTranslation();
  return (
    <TabsContent value="goals" className="flex-1 m-0 mt-2 min-h-0 px-2 pb-2">
      <div className="grid grid-cols-2 gap-2 mb-2">
        <Select value={goalType} onValueChange={setGoalType}>
          <SelectTrigger className="h-8">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="daily_words">{t("goalsPanel.dailyWords")}</SelectItem>
            <SelectItem value="session_words">{t("goalsPanel.sessionWords")}</SelectItem>
            <SelectItem value="total_words">{t("goalsPanel.totalWords")}</SelectItem>
          </SelectContent>
        </Select>
        <Input type="number" value={goalTargetValue} onChange={(event) => setGoalTargetValue(Number(event.target.value || 0))} />
      </div>
      <Button size="sm" onClick={() => void createGoal()} className="mb-2">
        {t("goalsPanel.createGoal")}
      </Button>
      <ScrollArea className="h-[calc(100%-2.5rem)] rounded-md border p-3 space-y-2 text-xs">
        {goals.map((goal) => (
          <div key={goal.id} className="rounded border p-2">
            <div className="flex items-center justify-between">
              <span>{goal.goalType}</span>
              <Badge variant={goal.status === "completed" ? "default" : "outline"}>{goal.status || "active"}</Badge>
            </div>
            <div className="text-muted-foreground mt-1">{`${goal.currentValue || 0}/${goal.targetValue || 0}`}</div>
            <div className="grid grid-cols-2 gap-2 mt-2">
              <Button
                size="sm"
                variant="outline"
                className="h-7"
                onClick={() => void updateGoal(String(goal.id), { status: goal.status === "completed" ? "active" : "completed" })}
              >
                {t("goalsPanel.toggleStatus")}
              </Button>
              <Button size="sm" variant="destructive" className="h-7" onClick={() => void deleteGoal(String(goal.id))}>
                {t("common.delete")}
              </Button>
            </div>
          </div>
        ))}
      </ScrollArea>
    </TabsContent>
  );
}
