import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useToast } from "@/components/ui/use-toast";
import { api } from "@/lib/api-client";
import { UploadCloud, FileText, CheckCircle2 } from "lucide-react";
import { FileImportJob } from "@/types";

const MaterialUpload = () => {
  const [file, setFile] = useState<File | null>(null);
  const [job, setJob] = useState<FileImportJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const { toast } = useToast();

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      if (selectedFile.type !== "text/plain") {
        toast({ variant: "destructive", title: "仅支持 .txt 文件" });
        return;
      }
      setFile(selectedFile);
      setJob(null);
    }
  };

  const handleUpload = async () => {
    if (!file) return;
    setIsUploading(true);
    try {
      const newJob = await api.materials.upload(file);
      setJob(newJob);
      
      // Simulate polling
      setTimeout(async () => {
        const completedJob = await api.materials.getUploadStatus(newJob.id);
        setJob(completedJob);
        setIsUploading(false);
        toast({ title: "解析完成", description: "文件已进入待审核队列" });
      }, 2000);
    } catch (error) {
      toast({ variant: "destructive", title: "上传失败" });
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
              <span>{job.status === 'completed' ? '100%' : '处理中...'}</span>
            </div>
            <Progress value={job.status === 'completed' ? 100 : 45} />
            {job.status === 'completed' && (
              <div className="flex items-center gap-2 text-green-600 text-sm mt-2">
                <CheckCircle2 className="h-4 w-4" /> 解析成功，请前往审核台查看
              </div>
            )}
          </div>
        )}

        <Button onClick={handleUpload} disabled={!file || isUploading} className="w-full">
          {isUploading ? "上传处理中..." : "开始上传"}
        </Button>
      </CardContent>
    </Card>
  );
};

export default MaterialUpload;
