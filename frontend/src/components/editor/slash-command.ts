import { Extension } from "@tiptap/core";
import Suggestion from "@tiptap/suggestion";
import { ReactRenderer } from "@tiptap/react";
import tippy from "tippy.js";
import SlashCommandList, { SlashCommandItem } from "./SlashCommandList";
import { 
  Heading1, 
  Heading2, 
  Heading3, 
  List, 
  ListOrdered, 
  Quote, 
  Sparkles, 
  User,
  Image as ImageIcon
} from "lucide-react";
import React from "react";
import { api } from "@/lib/api-client";
import { showError, showSuccess } from "@/utils/toast";

// 定义指令列表
const getSuggestionItems = ({ query }: { query: string }) => {
  const items: SlashCommandItem[] = [
    {
      title: "AI 续写",
      description: "让 AI 根据上下文继续创作",
      icon: React.createElement(Sparkles, { className: "h-4 w-4 text-purple-500" }),
      command: ({ editor }) => {
        const placeholder = "【AI 续写中...】";
        const from = editor.state.selection.from;
        editor.chain().focus().insertContent(placeholder).run();
        const to = from + placeholder.length;

        (async () => {
          try {
            const models = await api.ai.getModels();
            const modelId = models[0]?.id;
            if (!modelId) throw new Error("暂无可用 AI 模型");
            const contextText = editor.getText().slice(-1000);
            const resp = await api.ai.chat(
              [{ role: "user", content: "请根据上下文继续创作一段正文，保持中文小说风格。" }],
              modelId,
              { context: contextText }
            );
            const content = resp?.content || "";
            editor.chain().focus().deleteRange({ from, to }).insertContent(content).run();
            showSuccess("AI 续写完成");
          } catch (e: any) {
            editor.chain().focus().deleteRange({ from, to }).insertContent("【AI 续写失败】").run();
            showError(e?.message || "AI 续写失败");
          }
        })();
      },
    },
    {
      title: "一级标题",
      description: "大标题",
      icon: React.createElement(Heading1, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleHeading({ level: 1 }).run();
      },
    },
    {
      title: "二级标题",
      description: "中标题",
      icon: React.createElement(Heading2, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleHeading({ level: 2 }).run();
      },
    },
    {
      title: "三级标题",
      description: "小标题",
      icon: React.createElement(Heading3, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleHeading({ level: 3 }).run();
      },
    },
    {
      title: "无序列表",
      description: "创建一个项目符号列表",
      icon: React.createElement(List, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleBulletList().run();
      },
    },
    {
      title: "有序列表",
      description: "创建一个编号列表",
      icon: React.createElement(ListOrdered, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleOrderedList().run();
      },
    },
    {
      title: "引用",
      description: "插入一段引用文本",
      icon: React.createElement(Quote, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleBlockquote().run();
      },
    },
    {
      title: "插入角色卡",
      description: "插入一个角色信息块 (Mock)",
      icon: React.createElement(User, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().insertContent("<blockquote><strong>角色：</strong> [点击编辑姓名]<br/>设定：...</blockquote>").run();
      },
    },
  ];

  return items.filter((item) =>
    item.title.toLowerCase().startsWith(query.toLowerCase())
  ).slice(0, 10);
};

export const SlashCommand = Extension.create({
  name: "slashCommand",

  addOptions() {
    return {
      suggestion: {
        char: "/",
        command: ({ editor, range, props }: any) => {
          props.command({ editor, range });
        },
      },
    };
  },

  addProseMirrorPlugins() {
    return [
      Suggestion({
        editor: this.editor,
        ...this.options.suggestion,
      }),
    ];
  },
});

export const suggestionOptions = {
  items: getSuggestionItems,
  render: () => {
    let component: ReactRenderer;
    let popup: any;

    return {
      onStart: (props: any) => {
        component = new ReactRenderer(SlashCommandList, {
          props,
          editor: props.editor,
        });

        if (!props.clientRect) {
          return;
        }

        popup = tippy("body", {
          getReferenceClientRect: props.clientRect,
          appendTo: () => document.body,
          content: component.element,
          showOnCreate: true,
          interactive: true,
          trigger: "manual",
          placement: "bottom-start",
        });
      },

      onUpdate(props: any) {
        component.updateProps(props);

        if (!props.clientRect) {
          return;
        }

        popup[0].setProps({
          getReferenceClientRect: props.clientRect,
        });
      },

      onKeyDown(props: any) {
        if (props.event.key === "Escape") {
          popup[0].hide();
          return true;
        }

        return component.ref?.onKeyDown(props);
      },

      onExit() {
        popup[0].destroy();
        component.destroy();
      },
    };
  },
};
