import { useEffect, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { api } from "@/lib/api-client";
import { assistantPlainText, assistantTextDiff, sameAssistantDocument } from "@/lib/assistant-editor";
import { localizedErrorMessage } from "@/lib/error-messages";
import { Button } from "@/components/ui/button";
import { useWritingAssistantContext } from "./writing-assistant-context";

function ReadOnlyBody({ html, label }: { html: string; label: string }) {
  const editor = useEditor({ extensions: [StarterKit.configure({ link: { openOnClick: false } })], content: html, editable: false, editorProps: { attributes: { class: "prose prose-sm max-w-none break-words px-3 py-2", "aria-label": label } } });
  useEffect(() => { if (editor && editor.getHTML() !== html) editor.commands.setContent(html, { emitUpdate: false }); }, [editor, html]);
  return <EditorContent editor={editor} />;
}
export function AssistantProposalCard({ proposalId }: { proposalId: string }) {
  const assistant = useWritingAssistantContext()!;
  const { t } = useTranslation();
  const query = useQuery({ queryKey: ["assistant-proposal", assistant.id, assistant.branch, assistant.sceneId, proposalId, assistant.manuscriptVersion],
    queryFn: ({ signal }) => api.writingAssistant.proposal(assistant.id, assistant.sceneId, proposalId, signal), retry: false, staleTime: 0 });
  const proposal = query.data;
  const diff = useMemo(() => proposal ? assistantTextDiff(assistantPlainText(proposal.beforeHtml), assistantPlainText(proposal.afterHtml)) : [], [proposal]);
  if (query.error) return <p role="alert" className="text-destructive text-xs">{localizedErrorMessage(query.error)}</p>;
  if (!proposal) return <p>{t("assistant.loading")}</p>;
  const sourceMatches = sameAssistantDocument(assistant.content, proposal.status === "ACCEPTED" ? proposal.afterHtml : proposal.beforeHtml);
  const outside = proposal.actualHanAfter < proposal.minHan || proposal.actualHanAfter > proposal.maxHan;
  const pending = assistant.pendingDecision(proposal.id);
  const actionable = proposal.applicable && sourceMatches && !assistant.working && !assistant.locked && !assistant.writeBusy && !pending;
  const paragraphs = (html: string) => new DOMParser().parseFromString(html, "text/html").body.querySelectorAll(":scope > p").length;
  return <section aria-label={t("assistant.proposal")} className="mt-2 min-w-0 space-y-3 border-t pt-3">
    <div className="text-xs space-y-1"><p>{t(`assistant.proposal.${proposal.status}`)}</p>
      <p>{t("assistant.han", { before: proposal.actualHanBefore, after: proposal.actualHanAfter, min: proposal.minHan, max: proposal.maxHan, delta: Math.floor((proposal.minHan + proposal.maxHan) / 2) - proposal.actualHanAfter })}</p>
      <p>{t("assistant.paragraphs", { before: paragraphs(proposal.beforeHtml), after: paragraphs(proposal.afterHtml) })}</p>
      {outside && <p className="text-amber-700">{t("assistant.outside")}</p>}
      {(!sourceMatches || !proposal.applicable) && <p className="text-amber-700">{t("assistant.proposalStale")}</p>}
    </div>
    {pending && <div className="space-y-2 rounded border p-2 text-xs"><p>{t("assistant.receiptPending")}</p><Button size="sm" variant="outline" disabled={assistant.working || assistant.locked || assistant.writeBusy} onClick={() => void assistant.reconcileDecision(proposal.id)}>{t("assistant.checkSave")}</Button></div>}
    <details open><summary className="cursor-pointer font-medium">{t("assistant.diff")}</summary><div className="whitespace-pre-wrap leading-relaxed rounded border p-2 mt-2">{diff.map((part, index) => part.kind === "removed" ? <del key={index} className="bg-red-50 text-red-900">{part.text}</del> : part.kind === "added" ? <ins key={index} className="bg-green-50 text-green-900">{part.text}</ins> : <span key={index}>{part.text}</span>)}</div></details>
    <details><summary className="cursor-pointer">{t("assistant.fullBefore")}</summary><ReadOnlyBody html={proposal.beforeHtml} label={t("assistant.fullBefore")} /></details>
    <details><summary className="cursor-pointer">{t("assistant.fullAfter")}</summary><ReadOnlyBody html={proposal.afterHtml} label={t("assistant.fullAfter")} /></details>
    <div className="flex flex-wrap gap-2">
      {proposal.status === "READY" && <><Button size="sm" disabled={!actionable} onClick={() => { if (!outside || window.confirm(t("assistant.confirmOutside"))) void assistant.decide(proposal, "accept"); }}>{t("assistant.accept")}</Button><Button size="sm" variant="outline" disabled={assistant.working || !!pending} onClick={() => void assistant.decide(proposal, "reject")}>{t("assistant.reject")}</Button><span className="self-center text-xs text-muted-foreground">{t("assistant.keep")}</span></>}
      {proposal.status === "ACCEPTED" && <Button size="sm" variant="outline" disabled={!actionable} onClick={() => void assistant.decide(proposal, "undo")}>{t("assistant.undo")}</Button>}
      {["READY", "ACCEPTED"].includes(proposal.status) && <Button size="sm" variant="outline" disabled={assistant.working || !!pending || (proposal.status === "READY" && !sourceMatches)} onClick={() => assistant.refine(proposal)}>{t("assistant.refine")}</Button>}
    </div>
  </section>;
}
