import { useState } from "react";
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
  const { toast } = useToast();
  const { t } = useTranslation();

  const waitForTerminalStatus = async (jobId: string) => {
    let latest = await api.materials.getUploadStatus(jobId);
    setJob(latest);
    for (let attempt = 0; attempt < 60 && latest.status === "processing"; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 2_000));
      latest = await api.materials.getUploadStatus(jobId);
      setJob(latest);
    }
    return latest;
  };

  const queryUntilTerminal = async (jobId: string) => {
    setIsUploading(true);
    setUploadError("");
    try {
      const latest = await waitForTerminalStatus(jobId);
      if (latest.status === "failed") throw new Error(t("material.parseFailedMsg"));
      if (latest.status === "completed") {
        toast({ title: t("material.parseDone"), description: t("material.parseDoneDesc") });
      }
    } catch (error: any) {
      const message = localizedErrorMessage(error, "material.queryFailed");
      setUploadError(message);
      toast({ variant: "destructive", title: t("material.queryFailed"), description: message });
    } finally {
      setIsUploading(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      if (selectedFile.type !== "text/plain") {
        toast({ variant: "destructive", title: t("material.onlyTxt") });
        return;
      }
      setFile(selectedFile);
      setJob(null);
      setUploadError("");
    }
  };

  const handleUpload = async () => {
    if (!file) return;
    setIsUploading(true);
    setUploadError("");
    try {
      const newJob = await api.materials.upload(file);
      setJob(newJob);
      setIsUploading(false);
      await queryUntilTerminal(newJob.id);
    } catch (error: any) {
      const message = localizedErrorMessage(error, "material.queryFailed");
      setUploadError(message);
      toast({ variant: "destructive", title: t("material.uploadFailed"), description: message });
    } finally {
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
            onChange={handleFileChange} 
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
                <Button size="sm" variant="outline" onClick={() => void queryUntilTerminal(job.id)}>{t("material.continueQuery")}</Button>
              </div>
            )}
            {uploadError && (
              <div className="text-sm text-destructive">{uploadError}</div>
            )}
          </div>
        )}

        <Button onClick={handleUpload} disabled={!file || isUploading || job?.status === "processing"} className="w-full">
          {isUploading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />{t("material.uploading")}</> : uploadError ? t("material.retryUpload") : t("material.startUpload")}
        </Button>
      </CardContent>
    </Card>
  );
};

export default MaterialUpload;
