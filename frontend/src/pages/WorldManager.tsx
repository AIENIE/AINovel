import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Plus, MoreHorizontal, Database, Activity, Trash2 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api } from "@/lib/api-client";
import { World, WorldModuleDefinition } from "@/types";
import { showError, showSuccess } from "@/utils/toast";

type WorldCardStats = Record<string, { entries: number; completeness: number }>;

const WorldManager = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [worlds, setWorlds] = useState<World[]>([]);
  const [stats, setStats] = useState<WorldCardStats>({});
  const [loading, setLoading] = useState(true);
  const [definitions, setDefinitions] = useState<WorldModuleDefinition[]>([]);

  useEffect(() => {
    api.worlds.getDefinitions().then(setDefinitions).catch(() => setDefinitions([]));
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const list = await api.worlds.list();
      setWorlds(list);
      setLoading(false);

      (async () => {
        const next: WorldCardStats = {};
        await Promise.all(
          list.map(async (w) => {
            try {
              const detail = await api.worlds.getDetail(w.id);
              const entries = (detail.modules || []).reduce((acc, m) => {
                const v = Object.values(m.fields || {}).filter((x) => (x || "").trim().length > 0).length;
                return acc + v;
              }, 0);
              const totalFields =
                definitions.length > 0
                  ? definitions.reduce((acc, d) => acc + (d.fields || []).length, 0)
                  : Math.max(entries, 1);
              const completeness = totalFields > 0 ? Math.min(100, Math.round((entries / totalFields) * 100)) : 0;
              next[w.id] = { entries, completeness };
            } catch {
              next[w.id] = { entries: 0, completeness: 0 };
            }
          })
        );
        setStats(next);
      })();
    } catch (e: unknown) {
      setLoading(false);
      showError(e, "errors.loadFailed");
    }
  }, [definitions]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const handleDelete = async (worldId: string) => {
    if (!confirm(t("worlds.deleteConfirm"))) return;
    try {
      await api.worlds.delete(worldId);
      showSuccess("worlds.deleted");
      refresh();
    } catch (e: unknown) {
      showError(e, "errors.deleteFailed");
    }
  };

  const displayWorlds = useMemo(() => worlds, [worlds]);

  return (
    <div className="min-h-screen bg-muted/20">
      <header className="h-16 border-b bg-background px-6 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-4">
          <Link to="/dashboard">
            <Button variant="ghost" size="icon">
              <ArrowLeft className="h-5 w-5" />
            </Button>
          </Link>
          <h1 className="text-xl font-bold">{t("worlds.title")}</h1>
        </div>
        <Link to="/worlds/create">
          <Button variant="outline" className="border-blue-200 text-blue-700 hover:bg-blue-50">
            <Plus className="mr-2 h-4 w-4" /> {t("worlds.create")}
          </Button>
        </Link>
      </header>

      <main className="p-6 max-w-7xl mx-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Link to="/worlds/create" className="block h-full">
            <div className="border-2 border-dashed border-muted-foreground/20 rounded-xl hover:border-blue-500/50 hover:bg-blue-500/5 cursor-pointer flex flex-col items-center justify-center min-h-[240px] h-full transition-all duration-300 group bg-background/50">
              <div className="h-14 w-14 rounded-full bg-muted flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <Plus className="h-6 w-6 text-muted-foreground group-hover:text-blue-600" />
              </div>
              <span className="font-medium text-muted-foreground group-hover:text-blue-600">{t("worlds.createCard")}</span>
            </div>
          </Link>

          {loading && <div className="col-span-full text-sm text-muted-foreground">{t("common.loading")}</div>}
          {!loading && displayWorlds.length === 0 && <div className="col-span-full text-sm text-muted-foreground">{t("worlds.empty")}</div>}

          {displayWorlds.map((world) => {
            const s = stats[world.id];
            const entries = s?.entries ?? 0;
            const completeness = s?.completeness ?? 0;
            return (
              <div
                key={world.id}
                className="bg-background border rounded-xl overflow-hidden flex flex-col min-h-[240px] group cursor-pointer hover:shadow-lg hover:border-blue-500/50 transition-all duration-300"
                onClick={() => navigate(`/world-editor?id=${world.id}`)}
              >
                <div className="p-6 flex-1 flex flex-col">
                  <div className="flex justify-between items-start mb-4">
                    <div className="h-12 w-12 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center">
                      <Database className="h-6 w-6" />
                    </div>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8 -mr-2" onClick={(e) => e.stopPropagation()}>
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => navigate(`/world-editor?id=${world.id}`)}>{t("worlds.enterEdit")}</DropdownMenuItem>
                        {world.status === "draft" && (
                          <DropdownMenuItem className="text-destructive" onClick={() => handleDelete(world.id)}>
                            <Trash2 className="mr-2 h-4 w-4" /> {t("worlds.deleteDraft")}
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>

                  <h3 className="font-bold text-lg mb-1 group-hover:text-blue-600 transition-colors">{world.name}</h3>
                  <p className="text-sm text-muted-foreground mb-4 line-clamp-2">{world.tagline || t("worlds.noTagline")}</p>

                  <div className="mt-auto grid grid-cols-2 gap-4 pt-4 border-t">
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">{t("worlds.entries")}</div>
                      <div className="font-mono font-medium">{entries}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">{t("worlds.completeness")}</div>
                      <div className="flex items-center gap-1 font-mono font-medium text-blue-600">
                        <Activity className="h-3 w-3" /> {completeness}%
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </main>
    </div>
  );
};

export default WorldManager;
