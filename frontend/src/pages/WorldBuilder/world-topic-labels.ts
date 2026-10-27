type Topic = { id: string; title: string; createdAt: string };

export function worldTopicLabels(topics: Topic[], language: string) {
  const counts = new Map<string, number>();
  for (const topic of topics) counts.set(topic.title, (counts.get(topic.title) || 0) + 1);
  return new Map(topics.map(topic => {
    const date = new Date(topic.createdAt);
    const created = Number.isNaN(date.getTime()) ? "" : date.toLocaleString(language, {
      year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
    });
    // Only duplicate names need an identifier. Extend a colliding prefix rather than renumbering topics.
    const peers = topics.filter(other => other.title === topic.title && other.id !== topic.id);
    let length = 8;
    while (length < topic.id.length && peers.some(other => other.id.slice(0, length) === topic.id.slice(0, length))) length += 1;
    const reference = (counts.get(topic.title) || 0) > 1 ? topic.id.slice(0, length) : "";
    return [topic.id, { created, reference }] as const;
  }));
}
