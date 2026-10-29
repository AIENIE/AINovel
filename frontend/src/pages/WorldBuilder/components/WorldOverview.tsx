import { useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { localizedErrorMessage } from "@/lib/error-messages";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { WorldChangeList } from "./WorldVersionViews";
import type { WorldDiff } from "@/types";
import type { WorldEditorState } from "../useWorldDiscussion";
export function WorldOverview({editor,onIntegrate}:{editor:WorldEditorState;onIntegrate:()=>void}) {
  const {t}=useTranslation();const [diff,setDiff]=useState<WorldDiff|null>(null),[error,setError]=useState("");const world=editor.draft;if(!world)return null;
  return <section className="max-w-5xl space-y-5"><header className="flex flex-wrap items-start justify-between gap-4"><div className="max-w-2xl space-y-2"><h2 className="text-lg font-semibold">{t("world3.overview")}</h2><p className="text-sm leading-6 text-muted-foreground">{t("world3.overviewHelp")}</p></div><Button variant="outline" disabled={!editor.canAct()} onClick={onIntegrate}>{t("world3.integrate")}</Button></header>
    <label className="block space-y-2 text-sm"><span>{t("world3.overviewContent")}</span><Textarea className="min-h-[400px] leading-8" value={world.overview||""} onChange={e=>editor.edit(current=>({...current,overview:e.target.value}))}/></label>
    <div className="flex flex-wrap gap-3"><Button disabled={editor.saving||!!editor.remote||editor.conflict} onClick={()=>void editor.save()}>{t("world3.saveDraft")}</Button>{world.latestPublished&&<Button variant="outline" disabled={!editor.canAct()} onClick={async()=>{setError("");try{const result=await api.worlds.diff(world.id,world.latestPublished!.id,null,world.revision);setDiff({...result,changes:result.changes.filter(c=>c.kind==="METADATA"&&c.id==="overview")});}catch(e){setError(localizedErrorMessage(e));}}}>{t("world3.compareOverview")}</Button>}</div>
    {error&&<p role="alert" className="text-sm text-destructive">{error}</p>}{diff&&<WorldChangeList diff={diff}/>}<p className="text-xs text-muted-foreground">{t("world3.overviewHistory")}</p>
  </section>;
}
