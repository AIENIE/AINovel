import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { api } from "@/lib/api-client";
import { WorldPromptMetadata } from "@/types";

const WorldPromptHelpPage = () => {
  const navigate = useNavigate();
  const [metadata, setMetadata] = useState<WorldPromptMetadata | null>(null);

  useEffect(() => {
    api.prompts.getWorldMetadata().then(setMetadata);
  }, []);

  return (
    <div className="container mx-auto py-8 max-w-4xl">
      <Button variant="ghost" onClick={() => navigate("/settings")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" /> 返回设置
      </Button>
      
      <h1 className="text-3xl font-bold mb-6">世界观构建变量指南</h1>
      
      <Card className="mb-8">
        <CardHeader>
          <CardTitle>世界上下文变量</CardTitle>
          <CardDescription>变量会在世界观模块生成和字段精修时插入。</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>变量名</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>说明</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {metadata?.variables.map((variable) => (
                <TableRow key={variable.name}>
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
          <CardTitle>模块字段</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>模块</TableHead>
                <TableHead>字段</TableHead>
                <TableHead>长度上限</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {metadata?.modules.flatMap((module) => module.fields.map((field) => ({ module, field }))).map(({ module, field }) => (
                <TableRow key={`${module.key}-${field.key}`}>
                  <TableCell>{module.label}</TableCell>
                  <TableCell>{field.label}</TableCell>
                  <TableCell>{field.maxLength}</TableCell>
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
          {metadata?.examples.map((example) => (
            <pre key={example} className="whitespace-pre-wrap rounded border bg-muted p-3 text-sm">{example}</pre>
          )) ?? null}
        </CardContent>
      </Card>
    </div>
  );
};

export default WorldPromptHelpPage;
