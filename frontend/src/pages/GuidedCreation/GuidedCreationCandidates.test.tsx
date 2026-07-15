import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GuidedCreationCandidates from "./GuidedCreationCandidates";

describe("GuidedCreationCandidates", () => {
  it("renders exactly three story directions and selects one", () => {
    const onSelect = vi.fn();
    const candidates = [
      { candidateId: "c1", title: "潮汐钟", synopsis: "港口每天在退潮时停摆。" },
      { candidateId: "c2", title: "第六分钟", synopsis: "钟表匠只能记住六分钟。" },
      { candidateId: "c3", title: "无声塔", synopsis: "城市中央的钟塔吞掉声音。" },
    ];

    render(
      <GuidedCreationCandidates
        step="PREMISE"
        candidates={candidates}
        recommendedId="c2"
        selectedId="c2"
        onSelect={onSelect}
      />,
    );

    expect(screen.getByTestId("guided-candidates").querySelectorAll("button")).toHaveLength(3);
    expect(screen.getByText("推荐")).toBeTruthy();
    fireEvent.click(screen.getByText("无声塔"));
    expect(onSelect).toHaveBeenCalledWith(candidates[2]);
  });

  it("shows character ensembles with three to five members", () => {
    render(
      <GuidedCreationCandidates
        step="CHARACTERS"
        candidates={[
          { candidateId: "a", label: "追钟者", characters: [{ name: "林刻" }, { name: "雾岚" }, { name: "老莫" }] },
          { candidateId: "b", label: "守塔人", characters: [{ name: "岑音" }, { name: "陈昼" }, { name: "阿九" }] },
          { candidateId: "c", label: "逆时局", characters: [{ name: "周巡" }, { name: "苏禾" }, { name: "顾闻" }] },
        ]}
        recommendedId="a"
        selectedId="a"
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByText("追钟者")).toBeTruthy();
    expect(screen.getByText("林刻")).toBeTruthy();
    expect(screen.getByText("顾闻")).toBeTruthy();
  });
});
