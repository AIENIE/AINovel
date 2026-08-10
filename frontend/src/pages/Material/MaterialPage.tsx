import { useEffect, useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Library, Upload, PlusCircle } from "lucide-react";
import { useSearchParams } from "react-router-dom";

import MaterialList from "./tabs/MaterialList";
import MaterialCreateForm from "./tabs/MaterialCreateForm";
import MaterialUpload from "./tabs/MaterialUpload";
const MaterialPage = () => {
  const [params, setParams] = useSearchParams();
  const requested = params.get("tab");
  const normalized = requested === "create" || requested === "upload" ? requested : "list";
  const [activeTab, setActiveTab] = useState(normalized);
  useEffect(() => setActiveTab(normalized), [normalized]);
  const selectTab = (value: string) => {
    setActiveTab(value);
    const next = new URLSearchParams(params);
    next.set("tab", value);
    setParams(next);
  };

  return (
    <div className="h-full flex flex-col space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">素材库</h1>
      </div>

      <Tabs value={activeTab} onValueChange={selectTab} className="flex-1 flex flex-col">
        <TabsList className="grid w-full grid-cols-3 lg:w-[520px]">
          <TabsTrigger value="list" className="gap-2">
            <Library className="h-4 w-4" /> 素材列表
          </TabsTrigger>
          <TabsTrigger value="create" className="gap-2">
            <PlusCircle className="h-4 w-4" /> 手动创建
          </TabsTrigger>
          <TabsTrigger value="upload" className="gap-2">
            <Upload className="h-4 w-4" /> 批量导入
          </TabsTrigger>
        </TabsList>

        <div className="flex-1 mt-6 bg-card rounded-xl border shadow-sm p-6 min-h-[500px]">
          <TabsContent value="list" className="h-full m-0 border-0 p-0">
            <MaterialList />
          </TabsContent>
          <TabsContent value="create" className="h-full m-0 border-0 p-0">
            <MaterialCreateForm onSuccess={() => selectTab("list")} />
          </TabsContent>
          <TabsContent value="upload" className="h-full m-0 border-0 p-0">
            <MaterialUpload />
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
};

export default MaterialPage;
