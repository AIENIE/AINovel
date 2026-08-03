import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useToast } from "@/components/ui/use-toast";
import { api } from "@/lib/api-client";
import { UploadCloud, FileText, CheckCircle2, Loader2 } from "lucide-react";
import { FileImportJob } from "@/types";

const MaterialUpload = () => {
  const [file, setFile] = useState<File | null>(null);
  const [job, setJob] = useState<FileImportJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const { toast } = useToast();

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
      if (latest.status === "failed") throw new Error(latest.message || "文件解析失败");
      if (latest.status === "completed") {
        toast({ title: "解析完成", description: "素材已导入并进入待审核状态，可在素材列表中查看。" });
      }
    } catch (error: any) {
      const message = error?.message || "上传任务状态查询失败";
      setUploadError(message);
      toast({ variant: "destructive", title: "查询失败", description: message });
    } finally {
      setIsUploading(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      if (selectedFile.type !== "text/plain") {
        toast({ variant: "destructive", title: "仅支持 .txt 文件" });
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
      const message = error?.message || "上传任务状态查询失败";
      setUploadError(message);
      toast({ variant: "destructive", title: "上传失败", description: message });
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <Card className="max-w-2xl mx-auto">
      <CardHeader>
        <CardTitle>批量导入</CardTitle>
        <CardDescription>上传 TXT 文件，系统将自动解析并切分素材。</CardDescription>
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
            <span className="text-sm font-medium">点击选择文件 (仅支持 .txt)</span>
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
              <span>解析进度</span>
              <span>{job.status === "completed" ? "100%" : job.status === "failed" ? "失败" : `${job.progress || 0}%`}</span>
            </div>
            <Progress value={job.status === "completed" ? 100 : job.progress || 5} />
            {job.status === 'completed' && (
              <div className="flex items-center gap-2 text-green-600 text-sm mt-2">
                <CheckCircle2 className="h-4 w-4" /> 解析成功，素材已进入待审核状态
              </div>
            )}
            {job.status === "processing" && !isUploading && (
              <div className="flex items-center justify-between gap-3 text-sm text-muted-foreground">
                <span>任务仍在处理中，可继续查询，不会误报为完成。</span>
                <Button size="sm" variant="outline" onClick={() => void queryUntilTerminal(job.id)}>继续查询</Button>
              </div>
            )}
            {uploadError && (
              <div className="text-sm text-destructive">{uploadError}</div>
            )}
          </div>
        )}

        <Button onClick={handleUpload} disabled={!file || isUploading || job?.status === "processing"} className="w-full">
          {isUploading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />上传处理中...</> : uploadError ? "重试上传" : "开始上传"}
        </Button>
      </CardContent>
    </Card>
  );
};

export default MaterialUpload;
