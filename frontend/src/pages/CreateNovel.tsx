import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Sparkles, WandSparkles } from "lucide-react";
import { showError, showSuccess } from "@/utils/toast";
import { api, normalizeConceptionResult } from "@/lib/api-client";
import { runTrackedAiOperation } from "@/lib/ai-operation-store";

const CACHE_PREFIX = "ainovel.plot-planner.conception";

const CreateNovel = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [isLoading, setIsLoading] = useState(false);
  const [aiInit, setAiInit] = useState(true);

  const [title, setTitle] = useState("");
  const [genre, setGenre] = useState("fantasy");
  const [synopsis, setSynopsis] = useState("");

  const genres = [
    { value: "fantasy", label: t("genre.fantasy") },
    { value: "scifi", label: t("genre.scifi") },
    { value: "mystery", label: t("genre.mystery") },
    { value: "romance", label: t("genre.romance") },
    { value: "wuxia", label: t("genre.wuxia") },
    { value: "history", label: t("genre.history") },
    { value: "other", label: t("common.other") },
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setIsLoading(true);
    try {
      if (aiInit) {
        const operation = await runTrackedAiOperation(api.stories.startConception({ title: title.trim(), synopsis, genre, tone: "" }));
        const res = normalizeConceptionResult(operation.resultJson ? JSON.parse(operation.resultJson) : null);
        const storyId = res?.storyCard?.id;
        if (storyId) {
          try {
            window.sessionStorage.setItem(`${CACHE_PREFIX}:${storyId}`, JSON.stringify(res));
          } catch {
            // Ignore session storage failures.
          }
        }
        showSuccess("novels.created");
        if (storyId) navigate(`/workbench?storyId=${storyId}&tab=conception`);
        else navigate("/novels");
      } else {
        const story = await api.stories.create({ title: title.trim(), synopsis, genre, tone: "" });
        showSuccess("novels.created");
        navigate(`/workbench?storyId=${story.id}`);
      }
    } catch (err: unknown) {
      showError(err, "errors.createFailed");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="h-14 border-b flex items-center px-4 lg:px-8 bg-background/95 backdrop-blur sticky top-0 z-10">
        <Link to="/novels">
          <Button variant="ghost" size="icon" className="mr-2">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <h1 className="font-semibold">{t("novels.createTitle")}</h1>
        <Link to="/novels/quick-create" className="ml-auto">
          <Button variant="outline" size="sm">
            <WandSparkles className="mr-2 h-4 w-4" /> {t("novels.quickCreate")}
          </Button>
        </Link>
      </header>

      <main className="flex-1 flex justify-center p-4 lg:p-8">
        <div className="w-full max-w-2xl space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="space-y-2">
            <h2 className="text-3xl font-bold tracking-tight">{t("novels.newJourney")}</h2>
            <p className="text-muted-foreground">{t("novels.newJourneyDesc")}</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-8">
            <div className="space-y-4 p-6 border rounded-xl bg-card shadow-sm">
              <div className="space-y-2">
                <Label htmlFor="title">
                  {t("novels.titleLabel")} <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="title"
                  placeholder={t("novels.titlePlaceholder")}
                  required
                  className="text-lg font-medium"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="genre">{t("novels.genreLabel")}</Label>
                <Select value={genre} onValueChange={setGenre}>
                  <SelectTrigger>
                    <SelectValue placeholder={t("novels.genrePlaceholder")} />
                  </SelectTrigger>
                  <SelectContent>
                    {genres.map((g) => (
                      <SelectItem key={g.value} value={g.value}>
                        {g.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="summary">{t("novels.summaryLabel")}</Label>
                <Textarea
                  id="summary"
                  placeholder={t("novels.summaryPlaceholder")}
                  className="resize-none min-h-[100px]"
                  value={synopsis}
                  onChange={(e) => setSynopsis(e.target.value)}
                />
              </div>
            </div>

            <div className="p-6 border rounded-xl bg-purple-50 border-purple-200 space-y-4">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label className="text-base flex items-center gap-2 text-purple-900">
                    <Sparkles className="h-4 w-4 text-purple-600" />
                    {t("novels.aiInit")}
                  </Label>
                  <p className="text-sm text-purple-700">{t("novels.aiInitDesc")}</p>
                </div>
                <Switch checked={aiInit} onCheckedChange={setAiInit} />
              </div>
            </div>

            <div className="flex gap-4 pt-4">
              <Link to="/novels" className="flex-1">
                <Button variant="outline" type="button" className="w-full h-12">
                  {t("common.cancel")}
                </Button>
              </Link>
              <Button type="submit" className="flex-[2] h-12 text-lg" disabled={isLoading}>
                {isLoading ? t("novels.creating") : t("novels.createNow")}
              </Button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
};

export default CreateNovel;
