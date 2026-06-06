import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { api } from "@/lib/mock-api";
import { PromptMetadata } from "@/types";

const PromptHelpPage = () => {
  const navigate = useNavigate();
  const [metadata, setMetadata] = useState<PromptMetadata | null>(null);

  useEffect(() => {
    api.prompts.getWorkspaceMetadata().then(setMetadata);
  }, []);

  return (
    <div className="container mx-auto py-8 max-w-4xl">
      <Button variant="ghost" onClick={() => navigate("/settings")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" /> 返回设置
      </Button>
      
      <h1 className="text-3xl font-bold mb-6">提示词变量指南</h1>
      
      <Card className="mb-8">
        <CardHeader>
          <CardTitle>可用变量表</CardTitle>
          <CardDescription>按模板列出后端支持的变量。</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>模板</TableHead>
                <TableHead>变量名</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>说明</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {metadata?.templates.flatMap((template) => template.variables.map((variable) => ({ template: template.key, variable }))).map(({ template, variable }) => (
                <TableRow key={`${template}-${variable.name}`}>
                  <TableCell className="font-mono">{template}</TableCell>
                  <TableCell className="font-mono text-primary">{"{"}{variable.name}{"}"}</TableCell>
                  <TableCell>{variable.type}</TableCell>
                  <TableCell>{variable.description}</TableCell>
                </TableRow>
              )) ?? null}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card className="mb-8">
        <CardHeader>
          <CardTitle>函数</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>函数</TableHead>
                <TableHead>说明</TableHead>
                <TableHead>示例</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {metadata?.functions.map((fn) => (
                <TableRow key={fn.name}>
                  <TableCell className="font-mono text-primary">{fn.name}</TableCell>
                  <TableCell>{fn.description}</TableCell>
                  <TableCell className="font-mono">{fn.example}</TableCell>
                </TableRow>
              )) ?? null}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>示例</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {metadata?.syntaxTips.map((tip) => (
            <div key={tip.name}>
              <div className="font-medium">{tip.name}</div>
              <div className="text-sm text-muted-foreground">{tip.description}</div>
            </div>
          )) ?? null}
          {metadata?.examples.map((example) => (
            <pre key={example} className="whitespace-pre-wrap rounded border bg-muted p-3 text-sm">{example}</pre>
          )) ?? null}
        </CardContent>
      </Card>
    </div>
  );
};

export default PromptHelpPage;
