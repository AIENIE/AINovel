import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useToast } from "@/components/ui/use-toast";
import { api } from "@/lib/api-client";
import { UploadCloud, FileText, CheckCircle2, Loader2 } from "lucide-react";
import { FileImportJob } from "@/types";
import { localizedErrorMessage } from "@/lib/error-messages";

const MaterialUpload = () => {
  const [file, setFile] = useState<File | null>(null);
  const [job, setJob] = useState<FileImportJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [sourceState, setSourceState] = useState<Awaited<ReturnType<typeof api.evidence.statuses>>[number] | null>(null);
  const [hashing, setHashing] = useState(false);
  const pending = useRef(false);
  const intent = useRef("");
  const storedIntent = useRef("");
  const observation = useRef(0);
  const { toast } = useToast();
  const { t } = useTranslation();
  useEffect(() => () => { observation.current += 1; }, []);

  const waitForTerminalStatus = async (jobId: string) => {
    const epoch = observation.current;
    let latest = await api.materials.getUploadStatus(jobId);
    if (observation.current !== epoch) return null;
    setJob(latest);
    for (let attempt = 0; attempt < 60 && latest.status === "processing"; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 2_000));
      if (observation.current !== epoch) return null;
      latest = await api.materials.getUploadStatus(jobId);
      if (observation.current !== epoch) return null;
      setJob(latest);
    }
    if (latest.status === "completed" && storedIntent.current) sessionStorage.removeItem(storedIntent.current);
    if (latest.materialId && observation.current === epoch) {
      try {
        const states = await api.evidence.statuses();
        if (observation.current === epoch) setSourceState(states.find(state => state.materialId === latest.materialId) ?? null);
      } catch (error: unknown) {
        if (observation.current === epoch) setUploadError(localizedErrorMessage(error, "errors.loadFailed"));
      }
    }
    return latest;
  };

  const queryUntilTerminal = async (jobId: string) => {
    const latest = await waitForTerminalStatus(jobId);
    if (!latest) return;
    if (latest.status === "failed") throw new Error(t("material.parseFailedMsg"));
    if (latest.status === "completed") {
      toast({ title: t("material.parseDone"), description: t("material.parseDoneDesc") });
    }
  };

  const continueQuery = async (jobId: string) => {
    if (pending.current) return;
    pending.current = true; setIsUploading(true); setUploadError("");
    try { await queryUntilTerminal(jobId); }
    catch (error: unknown) { setUploadError(localizedErrorMessage(error, "material.queryFailed")); }
    finally { pending.current = false; setIsUploading(false); }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      if (!selectedFile.name.toLowerCase().endsWith(".txt") || selectedFile.size > 2_097_152) {
        toast({ variant: "destructive", title: t("material.onlyTxt") });
        return;
      }
      setFile(selectedFile);
      setJob(null);
      setUploadError("");
      setSourceState(null); setHashing(true); intent.current = ""; storedIntent.current = "";
      const epoch = ++observation.current;
      try {
        const digest = await crypto.subtle.digest("SHA-256", await selectedFile.arrayBuffer());
        const fingerprint = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
        const storageKey = `ainovel-upload-intent:${encodeURIComponent(selectedFile.name)}:${fingerprint}`;
        if (observation.current === epoch) {
          intent.current = sessionStorage.getItem(storageKey) || crypto.randomUUID();
          sessionStorage.setItem(storageKey, intent.current);
          storedIntent.current = storageKey;
        }
      } catch (error: unknown) { if (observation.current === epoch) setUploadError(localizedErrorMessage(error, "material.uploadFailed")); }
      finally { if (observation.current === epoch) setHashing(false); }
    }
  };

  const handleUpload = async () => {
    if (!file || !intent.current || pending.current || hashing) return;
    pending.current = true;
    setIsUploading(true);
    setUploadError("");
    const epoch = observation.current;
    try {
      const newJob = await api.materials.upload(file, intent.current);
      if (observation.current !== epoch) return;
      setJob(newJob);
      await queryUntilTerminal(newJob.id);
    } catch (error: unknown) {
      if (observation.current !== epoch) return;
      const message = localizedErrorMessage(error, "material.queryFailed");
      setUploadError(message);
      toast({ variant: "destructive", title: t("material.uploadFailed"), description: message });
    } finally {
      pending.current = false;
      setIsUploading(false);
    }
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>{t("material.batchImport")}</CardTitle>
        <CardDescription>{t("material.batchImportDesc")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="border-2 border-dashed rounded-lg p-8 text-center hover:bg-accent/50 transition-colors">
          <input 
            type="file" 
            accept=".txt" 
            onChange={e => void handleFileChange(e)}
            disabled={isUploading}
            className="hidden" 
            id="file-upload"
          />
          <label htmlFor="file-upload" className="cursor-pointer flex flex-col items-center gap-2">
            <UploadCloud className="h-10 w-10 text-muted-foreground" />
            <span className="text-sm font-medium">{t("material.selectFile")}</span>
            {file && (
              <div className="flex items-center gap-2 text-primary mt-2 bg-primary/10 px-3 py-1 rounded-full text-xs">
                <FileText className="h-3 w-3" />
                {file.name}
              </div>
            )}
          </label>
        </div>

        {job && (
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span>{t("material.parseProgress")}</span>
              <span>{job.status === "completed" ? "100%" : job.status === "failed" ? t("material.failedShort") : `${job.progress || 0}%`}</span>
            </div>
            <Progress value={job.status === "completed" ? 100 : job.progress || 5} />
            {job.status === 'completed' && (
              <div className="flex items-center gap-2 text-green-600 text-sm mt-2">
                <CheckCircle2 className="h-4 w-4" /> {t("material.parseSuccess")}
              </div>
            )}
            {job.status === "processing" && !isUploading && (
              <div className="flex items-center justify-between gap-3 text-sm text-muted-foreground">
                <span>{t("material.stillProcessing")}</span>
                <Button size="sm" variant="outline" onClick={() => void continueQuery(job.id)}>{t("material.continueQuery")}</Button>
              </div>
            )}
            {uploadError && (
              <div className="text-sm text-destructive">{uploadError}</div>
            )}
          </div>
        )}

        {sourceState && <p role="status" className="break-words text-sm text-muted-foreground">{t("evidence.uploadSaved")} · {sourceState.review === "approved" ? t("material.statusApproved") : t("evidence.savedReview")} · {t("evidence.basicState")}: {t(`evidence.basic.${sourceState.basic}`)} · {t("evidence.semanticState")}: {sourceState.semantic === "NOT_ENABLED" ? t("evidence.semanticOff") : sourceState.semantic}</p>}
        {!job && uploadError && <p role="alert" className="break-words text-sm text-destructive">{uploadError}</p>}

        <Button onClick={handleUpload} disabled={!file || hashing || isUploading || !intent.current || job?.status === "processing" || job?.status === "completed"} className="w-full">
          {isUploading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />{t("material.uploading")}</> : uploadError ? t("material.retryUpload") : t("material.startUpload")}
        </Button>
      </CardContent>
    </Card>
  );
};

export default MaterialUpload;
