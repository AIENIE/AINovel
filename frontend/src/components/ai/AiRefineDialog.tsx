import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowRight, Check, X } from "lucide-react";

interface AiRefineDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  originalText: string;
  refinedText: string;
  onConfirm: () => void;
  cost?: number;
}

const AiRefineDialog = ({ open, onOpenChange, originalText, refinedText, onConfirm, cost }: AiRefineDialogProps) => {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>{t("refine.dialogTitle")}</DialogTitle>
          <DialogDescription>
            {t("refine.dialogCost")} <span className="font-mono font-bold text-primary">{cost}</span>
          </DialogDescription>
        </DialogHeader>
        
        <div className="flex-1 grid grid-cols-2 gap-4 min-h-0 mt-4">
          <div className="flex flex-col border rounded-md overflow-hidden">
            <div className="bg-muted px-4 py-2 text-sm font-medium text-muted-foreground border-b">
              {t("refine.original")}
            </div>
            <ScrollArea className="flex-1 p-4 bg-muted/10">
              <div className="whitespace-pre-wrap text-sm leading-relaxed text-muted-foreground">
                {originalText}
              </div>
            </ScrollArea>
          </div>

          <div className="flex flex-col border rounded-md overflow-hidden border-primary/20">
            <div className="bg-primary/10 px-4 py-2 text-sm font-medium text-primary border-b border-primary/20 flex items-center gap-2">
              <ArrowRight className="h-4 w-4" /> {t("refine.refined")}
            </div>
            <ScrollArea className="flex-1 p-4 bg-primary/5">
              <div className="whitespace-pre-wrap text-sm leading-relaxed">
                {refinedText}
              </div>
            </ScrollArea>
          </div>
        </div>

        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            <X className="mr-2 h-4 w-4" /> {t("refine.discard")}
          </Button>
          <Button onClick={() => { onConfirm(); onOpenChange(false); }}>
            <Check className="mr-2 h-4 w-4" /> {t("refine.confirmReplace")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default AiRefineDialog;
