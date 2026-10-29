import type { NetworkObject } from "@/lib/api-client";
import { Extension, type Editor } from "@tiptap/core";
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
  User
} from "lucide-react";
import React from "react";
import { t } from "@/i18n";

// 指令列表（文案经 i18n 本地化）
const getSuggestionItems = ({ query }: { query: string }, onContinue?: (editor: Editor) => void) => {
  const items: SlashCommandItem[] = [
    {
      title: t("editor.continueWrite"),
      description: t("editor.continueWriteDesc"),
      icon: React.createElement(Sparkles, { className: "h-4 w-4 text-purple-500" }),
      command: ({ editor, range }) => {
        // Remove only the typed slash command. Opening the assistant never starts a paid call.
        editor.chain().focus().deleteRange(range).run();
        onContinue?.(editor);
      },
    },
    {
      title: t("editor.heading1"),
      description: t("editor.heading1Desc"),
      icon: React.createElement(Heading1, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleHeading({ level: 1 }).run();
      },
    },
    {
      title: t("editor.heading2"),
      description: t("editor.heading2Desc"),
      icon: React.createElement(Heading2, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleHeading({ level: 2 }).run();
      },
    },
    {
      title: t("editor.heading3"),
      description: t("editor.heading3Desc"),
      icon: React.createElement(Heading3, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleHeading({ level: 3 }).run();
      },
    },
    {
      title: t("editor.bulletList"),
      description: t("editor.bulletListDesc"),
      icon: React.createElement(List, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleBulletList().run();
      },
    },
    {
      title: t("editor.orderedList"),
      description: t("editor.orderedListDesc"),
      icon: React.createElement(ListOrdered, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleOrderedList().run();
      },
    },
    {
      title: t("editor.quote"),
      description: t("editor.quoteDesc"),
      icon: React.createElement(Quote, { className: "h-4 w-4" }),
      command: ({ editor }) => {
        editor.chain().focus().toggleBlockquote().run();
      },
    },
    {
      title: t("editor.characterCard"),
      description: t("editor.characterCardDesc"),
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
        command: ({ editor, range, props }: NetworkObject) => {
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
  items: (args: { query: string }) => getSuggestionItems(args),
  render: () => {
    let component: ReactRenderer;
    let popup: NetworkObject;

    return {
      onStart: (props: NetworkObject) => {
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

      onUpdate(props: NetworkObject) {
        component.updateProps(props);

        if (!props.clientRect) {
          return;
        }

        popup[0].setProps({
          getReferenceClientRect: props.clientRect,
        });
      },

      onKeyDown(props: NetworkObject) {
        if (props.event.key === "Escape") {
          popup[0].hide();
          return true;
        }

        return ((component.ref as { onKeyDown?: (nextProps: NetworkObject) => boolean } | null)?.onKeyDown?.(props)) ?? false;
      },

      onExit() {
        popup[0].destroy();
        component.destroy();
      },
    };
  },
};

export const createSuggestionOptions = (onContinue: (editor: Editor) => void) => ({
  ...suggestionOptions, items: (args: { query: string }) => getSuggestionItems(args, onContinue),
});
