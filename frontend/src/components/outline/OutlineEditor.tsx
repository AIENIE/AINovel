import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { SortableChapterItem, Chapter } from "./SortableChapterItem";
import { Button } from "@/components/ui/button";
import { Plus, Sparkles } from "lucide-react";

// Mock Data (示例内容，不翻译)
const MOCK_CHAPTERS: Chapter[] = [
  { id: "c1", title: "霓虹雨", summary: "主角在港口醒来，发现自己失去了记忆，只有一把生锈的枪。", order: 0 },
  { id: "c2", title: "地下诊所", summary: "为了寻找线索，主角前往地下城的黑市诊所，遇到了神秘的医生。", order: 1 },
  { id: "c3", title: "公司特工", summary: "荒坂公司的特工开始追踪主角，一场追逐战在贫民窟展开。", order: 2 },
  { id: "c4", title: "觉醒", summary: "主角发现自己其实是仿生人，记忆是被植入的。", order: 3 },
];

const OutlineEditor = () => {
  const { t } = useTranslation();
  const [chapters, setChapters] = useState<Chapter[]>(MOCK_CHAPTERS);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (over && active.id !== over.id) {
      setChapters((items) => {
        const oldIndex = items.findIndex((item) => item.id === active.id);
        const newIndex = items.findIndex((item) => item.id === over.id);

        // In a real app, you would call an API here to persist the new order
        // api.updateChapterOrder(items[oldIndex].id, newIndex);
        
        return arrayMove(items, oldIndex, newIndex);
      });
    }
  };

  const handleAddChapter = () => {
    const newChapter: Chapter = {
      id: `new-${Date.now()}`,
      title: t("outline.newChapter"),
      summary: t("outline.editSummary"),
      order: chapters.length,
    };
    setChapters([...chapters, newChapter]);
  };

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">{t("outline.title")}</h2>
        <div className="flex gap-2">
          <Button variant="outline" size="sm">
            <Sparkles className="mr-2 h-4 w-4" />
            {t("outline.logicCheck")}
          </Button>
          <Button size="sm" onClick={handleAddChapter}>
            <Plus className="mr-2 h-4 w-4" />
            {t("outline.addChapter")}
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto pr-2">
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={chapters}
            strategy={verticalListSortingStrategy}
          >
            {chapters.map((chapter, index) => (
              <SortableChapterItem 
                key={chapter.id} 
                chapter={chapter} 
                index={index} 
              />
            ))}
          </SortableContext>
        </DndContext>
        
        {chapters.length === 0 && (
          <div className="text-center py-12 border-2 border-dashed rounded-lg text-muted-foreground">
            <p>{t("outline.empty")}</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default OutlineEditor;
