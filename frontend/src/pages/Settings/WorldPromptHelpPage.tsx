import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { api } from "@/lib/api-client";
import { WorldPromptMetadata } from "@/types";

const WorldPromptHelpPage = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [metadata, setMetadata] = useState<WorldPromptMetadata | null>(null);

  useEffect(() => {
    api.prompts.getWorldMetadata().then(setMetadata);
  }, []);

  return (
    <div className="container mx-auto py-8 max-w-4xl">
      <Button variant="ghost" onClick={() => navigate("/settings")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" /> {t("promptHelp.backToSettings")}
      </Button>
      
      <h1 className="text-3xl font-bold mb-6">{t("worldPromptHelp.title")}</h1>
      
      <Card className="mb-8">
        <CardHeader>
          <CardTitle>{t("worldPromptHelp.contextTitle")}</CardTitle>
          <CardDescription>{t("worldPromptHelp.contextDesc")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("promptHelp.colVariable")}</TableHead>
                <TableHead>{t("promptHelp.colType")}</TableHead>
                <TableHead>{t("promptHelp.colDescription")}</TableHead>
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
          <CardTitle>{t("worldPromptHelp.moduleFieldsTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("worldPromptHelp.colModule")}</TableHead>
                <TableHead>{t("worldPromptHelp.colField")}</TableHead>
                <TableHead>{t("worldPromptHelp.colMaxLength")}</TableHead>
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
          <CardTitle>{t("promptHelp.functionsTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("promptHelp.colFunction")}</TableHead>
                <TableHead>{t("promptHelp.colDescription")}</TableHead>
                <TableHead>{t("promptHelp.colExample")}</TableHead>
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
          <CardTitle>{t("promptHelp.examplesTitle")}</CardTitle>
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
