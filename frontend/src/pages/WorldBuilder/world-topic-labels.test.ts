import { expect, it } from "vitest";
import { worldTopicLabels } from "./world-topic-labels";

it("distinguishes same-second duplicate names without depending on list order or update time", () => {
  const topics = [
    { id: "11111111-aaaa", title: "世界概览整合", createdAt: "2026-10-27T08:00:00Z" },
    { id: "11111111-bbbb", title: "世界概览整合", createdAt: "2026-10-27T08:00:00Z" },
    { id: "33333333-cccc", title: "独立讨论", createdAt: "bad historical date" },
  ];
  const labels = worldTopicLabels(topics, "zh-CN");
  expect(labels.get(topics[0].id)?.reference).toBe("11111111-a");
  expect(labels.get(topics[1].id)?.reference).toBe("11111111-b");
  expect(labels.get(topics[0].id)?.created).toBe(labels.get(topics[1].id)?.created);
  expect(worldTopicLabels([...topics].reverse(), "zh-CN")).toEqual(labels);
  expect(labels.get(topics[2].id)).toEqual({ created: "", reference: "" });
});
