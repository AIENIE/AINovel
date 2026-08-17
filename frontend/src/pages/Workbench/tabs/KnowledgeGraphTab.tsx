import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { useTranslation } from "react-i18next";
import { api, type NetworkObject } from "@/lib/api-client";
import { Story } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useToast } from "@/components/ui/use-toast";
import { localizedErrorMessage } from "@/lib/error-messages";

interface KnowledgeGraphTabProps {
  initialStoryId?: string;
}

type GraphEdge = {
  id: string;
  source: string;
  target: string;
  label: string;
};

const GRAPH_WIDTH = 960;
const GRAPH_HEIGHT = 560;

const nodeStyle = (type: string) => {
  switch (type) {
    case "character":
      return { fill: "#d1fae5", stroke: "#059669", text: "#065f46" };
    case "location":
      return { fill: "#dbeafe", stroke: "#2563eb", text: "#1d4ed8" };
    case "event":
      return { fill: "#fef3c7", stroke: "#d97706", text: "#92400e" };
    case "item":
      return { fill: "#ccfbf1", stroke: "#0f766e", text: "#115e59" };
    case "concept":
      return { fill: "#ede9fe", stroke: "#7c3aed", text: "#5b21b6" };
    default:
      return { fill: "#f4f4f5", stroke: "#52525b", text: "#27272a" };
  }
};

const normalizeEdge = (edge: NetworkObject, index: number): GraphEdge | null => {
  const source = String(edge?.source ?? edge?.sourceId ?? edge?.from ?? "");
  const target = String(edge?.target ?? edge?.targetId ?? edge?.to ?? "");
  if (!source || !target) return null;
  return {
    id: String(edge?.id || `${source}-${target}-${index}`),
    source,
    target,
    label: String(edge?.relationType || edge?.type || edge?.label || ""),
  };
};

const buildCircularLayout = (nodes: NetworkObject[]) => {
  const centerX = GRAPH_WIDTH / 2;
  const centerY = GRAPH_HEIGHT / 2;
  const radius = Math.min(GRAPH_WIDTH, GRAPH_HEIGHT) * 0.34;
  const total = Math.max(1, nodes.length);
  const positions: Record<string, { x: number; y: number }> = {};
  nodes.forEach((node, index) => {
    const angle = (Math.PI * 2 * index) / total;
    positions[String(node.id)] = {
      x: centerX + Math.cos(angle) * radius,
      y: centerY + Math.sin(angle) * radius,
    };
  });
  return positions;
};

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

const KnowledgeGraphTab = ({ initialStoryId }: KnowledgeGraphTabProps) => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const svgRef = useRef<SVGSVGElement | null>(null);

  const [stories, setStories] = useState<Story[]>([]);
  const [storyId, setStoryId] = useState(initialStoryId || "");
  const [keyword, setKeyword] = useState("");
  const [graphData, setGraphData] = useState<NetworkObject>({ nodes: [], edges: [] });
  const [queryData, setQueryData] = useState<NetworkObject>({ nodes: [], edges: [] });
  const [selectedNode, setSelectedNode] = useState<NetworkObject | null>(null);
  const [visibleTypes, setVisibleTypes] = useState<Record<string, boolean>>({});
  const [positions, setPositions] = useState<Record<string, { x: number; y: number }>>({});
  const [draggingNodeId, setDraggingNodeId] = useState("");
  const [relationSourceId, setRelationSourceId] = useState("");
  const [relationTargetId, setRelationTargetId] = useState("");
  const [relationType, setRelationType] = useState("related_to");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.stories
      .list()
      .then((list) => {
        setStories(list);
        setStoryId((current) => current || (initialStoryId && list.some((story) => story.id === initialStoryId) ? initialStoryId : list[0]?.id || ""));
      })
      .catch((error: NetworkObject) => toast({ variant: "destructive", title: t("errors.loadStoriesFailed"), description: localizedErrorMessage(error, "errors.loadStoriesFailed") }));
  }, [initialStoryId, t, toast]);

  const loadGraph = useCallback(async () => {
    if (!storyId) return;
    setLoading(true);
    try {
      const data = await api.v2.context.getGraph(storyId);
      setGraphData(data);
      setQueryData(data);
      if (data.nodes?.length) {
        setSelectedNode(data.nodes[0]);
      } else {
        setSelectedNode(null);
      }
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("graph.loadFailed"), description: localizedErrorMessage(error, "graph.loadFailed") });
    } finally {
      setLoading(false);
    }
  }, [storyId, t, toast]);

  useEffect(() => {
    void loadGraph();
  }, [loadGraph]);

  const queryGraph = async () => {
    if (!storyId) return;
    const term = keyword.trim();
    if (!term) {
      setQueryData(graphData);
      return;
    }
    await runKeywordQuery(term);
  };

  const runKeywordQuery = async (term: string) => {
    if (!storyId) return;
    setLoading(true);
    try {
      const data = await api.v2.context.queryGraph(storyId, term.trim());
      setQueryData(data);
      if (data.nodes?.length) {
        setSelectedNode(data.nodes[0]);
      }
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("graph.queryFailed"), description: localizedErrorMessage(error, "graph.queryFailed") });
    } finally {
      setLoading(false);
    }
  };

  const nodeTypes = useMemo(() => {
    const all = new Set<string>();
    for (const node of graphData.nodes || []) {
      all.add(String(node.type || "custom"));
    }
    return [...all];
  }, [graphData]);

  useEffect(() => {
    setVisibleTypes((prev) => {
      const next: Record<string, boolean> = {};
      nodeTypes.forEach((type) => {
        next[type] = Object.prototype.hasOwnProperty.call(prev, type) ? prev[type] : true;
      });
      return next;
    });
  }, [nodeTypes]);

  useEffect(() => {
    const nodes = queryData.nodes || [];
    if (!nodes.length) {
      setPositions({});
      return;
    }
    const fallback = buildCircularLayout(nodes);
    setPositions((prev) => {
      const next: Record<string, { x: number; y: number }> = {};
      nodes.forEach((node: NetworkObject) => {
        const id = String(node.id);
        next[id] = prev[id] || fallback[id];
      });
      return next;
    });
  }, [queryData]);

  const normalizedEdges = useMemo(() => {
    const list: GraphEdge[] = [];
    (queryData.edges || []).forEach((edge: NetworkObject, index: number) => {
      const normalized = normalizeEdge(edge, index);
      if (normalized) list.push(normalized);
    });
    return list;
  }, [queryData]);

  const visibleNodes = useMemo(
    () =>
      (queryData.nodes || []).filter((node: NetworkObject) => {
        const type = String(node.type || "custom");
        return visibleTypes[type] ?? true;
      }),
    [queryData, visibleTypes],
  );

  const visibleNodeMap = useMemo(
    () => Object.fromEntries(visibleNodes.map((node: NetworkObject) => [String(node.id), node])),
    [visibleNodes],
  );

  const visibleEdges = useMemo(
    () => normalizedEdges.filter((edge) => visibleNodeMap[edge.source] && visibleNodeMap[edge.target]),
    [normalizedEdges, visibleNodeMap],
  );

  const relationEdges = useMemo(() => {
    if (!selectedNode) return [] as GraphEdge[];
    const id = String(selectedNode.id);
    return visibleEdges.filter((edge) => edge.source === id || edge.target === id);
  }, [selectedNode, visibleEdges]);

  useEffect(() => {
    if (!selectedNode?.id) return;
    setRelationSourceId(String(selectedNode.id));
  }, [selectedNode]);

  useEffect(() => {
    if (relationTargetId) return;
    const firstTarget = visibleNodes.find((node: NetworkObject) => String(node.id) !== relationSourceId);
    if (firstTarget) {
      setRelationTargetId(String(firstTarget.id));
    }
  }, [relationSourceId, relationTargetId, visibleNodes]);

  const nodeStats = useMemo(() => {
    const stats: Record<string, number> = {};
    for (const node of graphData.nodes || []) {
      const key = String(node.type || "custom");
      stats[key] = (stats[key] || 0) + 1;
    }
    return stats;
  }, [graphData]);

  const highlightTerm = keyword.trim().toLowerCase();

  const onNodeMouseDown = (event: MouseEvent, nodeId: string) => {
    event.preventDefault();
    setDraggingNodeId(nodeId);
  };

  const onSvgMouseMove = (event: MouseEvent<SVGSVGElement>) => {
    if (!draggingNodeId || !svgRef.current) return;
    const rect = svgRef.current.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * GRAPH_WIDTH;
    const y = ((event.clientY - rect.top) / rect.height) * GRAPH_HEIGHT;
    setPositions((prev) => ({
      ...prev,
      [draggingNodeId]: {
        x: clamp(x, 24, GRAPH_WIDTH - 24),
        y: clamp(y, 24, GRAPH_HEIGHT - 24),
      },
    }));
  };

  const resetLayout = () => {
    setPositions(buildCircularLayout(queryData.nodes || []));
  };

  const createRelationship = async () => {
    if (!storyId) return;
    if (!relationSourceId || !relationTargetId) {
      toast({ variant: "destructive", title: t("graph.selectBothNodes") });
      return;
    }
    if (relationSourceId === relationTargetId) {
      toast({ variant: "destructive", title: t("graph.sameNodeError") });
      return;
    }
    setLoading(true);
    try {
      await api.v2.context.createRelationship(storyId, {
        source: relationSourceId,
        target: relationTargetId,
        relationType: relationType.trim() || "related_to",
      });
      await loadGraph();
      toast({ title: t("graph.relationCreated") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.createRelationFailed"), description: localizedErrorMessage(error, "errors.createRelationFailed") });
    } finally {
      setLoading(false);
    }
  };

  const deleteRelationship = async (relationshipId: string) => {
    if (!storyId) return;
    setLoading(true);
    try {
      await api.v2.context.deleteRelationship(storyId, relationshipId);
      await loadGraph();
      toast({ title: t("graph.relationDeleted") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.deleteRelationFailed"), description: localizedErrorMessage(error, "errors.deleteRelationFailed") });
    } finally {
      setLoading(false);
    }
  };

  const deleteSelectedNode = async () => {
    if (!storyId || !selectedNode?.id) return;
    const ok = window.confirm(t("graph.deleteNodeConfirm", { name: selectedNode.label || selectedNode.name }));
    if (!ok) return;
    setLoading(true);
    try {
      await api.v2.context.deleteLorebook(storyId, String(selectedNode.id));
      await loadGraph();
      toast({ title: t("graph.nodeDeleted") });
    } catch (error: unknown) {
      toast({ variant: "destructive", title: t("errors.deleteNodeFailed"), description: localizedErrorMessage(error, "errors.deleteNodeFailed") });
    } finally {
      setLoading(false);
    }
  };

  const selectedStyle = nodeStyle(String(selectedNode?.type || "custom"));

  return (
    <div className="h-full flex flex-col gap-4">
      <Card>
        <CardContent className="pt-6 flex flex-wrap items-end gap-3">
          <div className="w-[280px]">
            <Label>{t("common.story")}</Label>
            <Select value={storyId} onValueChange={setStoryId}>
              <SelectTrigger>
                <SelectValue placeholder={t("common.selectStory")} />
              </SelectTrigger>
              <SelectContent>
                {stories.map((story) => (
                  <SelectItem key={story.id} value={story.id}>
                    {story.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-[320px]">
            <Label>{t("graph.entitySearch")}</Label>
            <div className="flex gap-2">
              <Input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") void queryGraph();
                }}
                placeholder={t("graph.searchPlaceholder")}
              />
              <Button onClick={() => void queryGraph()} disabled={!storyId || loading}>
                {t("graph.search")}
              </Button>
            </div>
          </div>
          <Button variant="secondary" onClick={() => void loadGraph()} disabled={!storyId || loading}>
            {t("graph.refreshGraph")}
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setQueryData(graphData);
              setKeyword("");
            }}
            disabled={loading}
          >
            {t("graph.clearFilter")}
          </Button>
          <Button variant="outline" onClick={resetLayout} disabled={loading || !(queryData.nodes || []).length}>
            {t("graph.resetLayout")}
          </Button>
        </CardContent>
      </Card>

      <div className="grid flex-1 gap-4 lg:grid-cols-[1fr_320px] min-h-0">
        <Card className="min-h-0 flex flex-col">
          <CardHeader>
            <CardTitle>{t("graph.title")}</CardTitle>
            <CardDescription>
              {t("graph.nodeRelationCount", { nodes: visibleNodes.length, total: queryData.nodes?.length || 0, edges: visibleEdges.length })}
            </CardDescription>
          </CardHeader>
          <CardContent className="min-h-0 flex-1 space-y-3">
            <div className="flex flex-wrap gap-2">
              {nodeTypes.map((type) => (
                <button
                  key={type}
                  type="button"
                  className={`rounded-full border px-3 py-1 text-xs transition ${
                    visibleTypes[type] === false ? "border-border text-muted-foreground" : "border-primary/50 bg-primary/10"
                  }`}
                  onClick={() => setVisibleTypes((prev) => ({ ...prev, [type]: !(prev[type] ?? true) }))}
                >
                  {type}
                </button>
              ))}
            </div>
            <div className="h-[560px] rounded-md border bg-muted/20 relative overflow-hidden">
              {!visibleNodes.length ? (
                <div className="h-full flex items-center justify-center text-sm text-muted-foreground">{t("graph.noVisibleNodes")}</div>
              ) : (
                <svg
                  ref={svgRef}
                  viewBox={`0 0 ${GRAPH_WIDTH} ${GRAPH_HEIGHT}`}
                  className="h-full w-full"
                  onMouseMove={onSvgMouseMove}
                  onMouseUp={() => setDraggingNodeId("")}
                  onMouseLeave={() => setDraggingNodeId("")}
                >
                  {visibleEdges.map((edge) => {
                    const source = positions[edge.source];
                    const target = positions[edge.target];
                    if (!source || !target) return null;
                    const midX = (source.x + target.x) / 2;
                    const midY = (source.y + target.y) / 2;
                    return (
                      <g key={edge.id}>
                        <line x1={source.x} y1={source.y} x2={target.x} y2={target.y} stroke="#94a3b8" strokeWidth={1.2} opacity={0.7} />
                        {!!edge.label && (
                          <text x={midX} y={midY - 4} textAnchor="middle" fontSize="11" fill="#475569">
                            {edge.label}
                          </text>
                        )}
                      </g>
                    );
                  })}
                  {visibleNodes.map((node: NetworkObject) => {
                    const id = String(node.id);
                    const pos = positions[id];
                    if (!pos) return null;
                    const selected = String(selectedNode?.id) === id;
                    const matched =
                      !!highlightTerm &&
                      (String(node.label || node.name || "").toLowerCase().includes(highlightTerm) ||
                        String(node.type || "").toLowerCase().includes(highlightTerm));
                    const style = nodeStyle(String(node.type || "custom"));
                    return (
                      <g
                        key={id}
                        transform={`translate(${pos.x}, ${pos.y})`}
                        className="cursor-pointer"
                        onMouseDown={(event) => onNodeMouseDown(event, id)}
                        onClick={() => setSelectedNode(node)}
                        onDoubleClick={() => {
                          const nodeKeyword = String(node.label || node.name || "");
                          setKeyword(nodeKeyword);
                          void runKeywordQuery(nodeKeyword);
                        }}
                      >
                        {matched && <circle r={22} fill={style.stroke} opacity={0.2} className="animate-pulse" />}
                        <circle r={selected ? 18 : 15} fill={style.fill} stroke={selected ? "#0f172a" : style.stroke} strokeWidth={selected ? 2.2 : 1.6} />
                        <text x={0} y={4} textAnchor="middle" fontSize={10} fill={style.text}>
                          {(node.label || node.name || "").slice(0, 8)}
                        </text>
                      </g>
                    );
                  })}
                </svg>
              )}
            </div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>{t("graph.nodeStats")}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {Object.entries(nodeStats).map(([type, count]) => (
                <div key={type} className="flex items-center justify-between text-sm">
                  <span>{type}</span>
                  <Badge variant="secondary">{count}</Badge>
                </div>
              ))}
              {!Object.keys(nodeStats).length && <p className="text-sm text-muted-foreground">{t("graph.noStats")}</p>}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>{t("graph.nodeDetails")}</CardTitle>
            </CardHeader>
            <CardContent>
              {selectedNode ? (
                <div className="space-y-3 text-sm">
                  <div className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs" style={{ borderColor: selectedStyle.stroke, color: selectedStyle.text }}>
                    {selectedNode.type || "custom"}
                  </div>
                  <div>
                    <span className="text-muted-foreground">{t("graph.name")}：</span>
                    <span>{selectedNode.label || selectedNode.name}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">ID：</span>
                    <span className="font-mono text-xs break-all">{selectedNode.id}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">{t("graph.relations")}：</span>
                    <ScrollArea className="mt-2 h-28 rounded border p-2">
                      <div className="space-y-1 text-xs">
                        {relationEdges.map((edge) => (
                          <div key={edge.id} className="rounded border p-1 flex items-center justify-between gap-2">
                            <span className="truncate">
                              {edge.source === String(selectedNode.id) ? "->" : "<-"} {edge.label || t("graph.related")} ·{" "}
                              {edge.source === String(selectedNode.id) ? edge.target : edge.source}
                            </span>
                            <Button size="sm" variant="ghost" className="h-6 px-2" onClick={() => void deleteRelationship(edge.id)}>
                              {t("common.delete")}
                            </Button>
                          </div>
                        ))}
                        {!relationEdges.length && <div className="text-muted-foreground">{t("graph.noVisibleRelations")}</div>}
                      </div>
                    </ScrollArea>
                  </div>
                  <div className="space-y-2 rounded border p-2">
                    <div className="text-xs text-muted-foreground">{t("graph.relationEdit")}</div>
                    <Select value={relationSourceId} onValueChange={setRelationSourceId}>
                      <SelectTrigger className="h-8">
                        <SelectValue placeholder={t("graph.sourceNode")} />
                      </SelectTrigger>
                      <SelectContent>
                        {visibleNodes.map((node: NetworkObject) => (
                          <SelectItem key={`source-${node.id}`} value={String(node.id)}>
                            {node.label || node.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Select value={relationTargetId} onValueChange={setRelationTargetId}>
                      <SelectTrigger className="h-8">
                        <SelectValue placeholder={t("graph.targetNode")} />
                      </SelectTrigger>
                      <SelectContent>
                        {visibleNodes
                          .filter((node: NetworkObject) => String(node.id) !== relationSourceId)
                          .map((node: NetworkObject) => (
                            <SelectItem key={`target-${node.id}`} value={String(node.id)}>
                              {node.label || node.name}
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                    <Input value={relationType} onChange={(event) => setRelationType(event.target.value)} placeholder={t("graph.relationTypePlaceholder")} />
                    <Button size="sm" variant="secondary" onClick={() => void createRelationship()}>
                      {t("graph.createRelation")}
                    </Button>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        const nodeKeyword = String(selectedNode.label || selectedNode.name || "");
                        setKeyword(nodeKeyword);
                        void runKeywordQuery(nodeKeyword);
                      }}
                    >
                      {t("graph.querySubgraph")}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setSelectedNode(null);
                      }}
                    >
                      {t("graph.clearSelection")}
                    </Button>
                    <Button size="sm" variant="destructive" onClick={() => void deleteSelectedNode()}>
                      {t("graph.deleteNode")}
                    </Button>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">{t("graph.clickNodeHint")}</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default KnowledgeGraphTab;
