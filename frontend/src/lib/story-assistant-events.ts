export const STORY_RESOURCE_CHANGED = "ainovel:story-resource-changed";
export const STORY_ASSISTANT_UPDATED = "ainovel:story-assistant-updated";
export type StoryResourceChange = { storyId: string; resourceType: "CHARACTER" | "CHAPTER" | "SCENE"; targetId?: string | null; outlineId?: string | null };
export function notifyStoryResourceChanged(detail: StoryResourceChange) {
  window.dispatchEvent(new CustomEvent(STORY_RESOURCE_CHANGED, { detail }));
}
