const flattenSearchValues = (value: unknown): string[] => {
  if (value == null) return [];
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return [String(value)];
  }
  if (value instanceof Date) {
    return [value.toISOString()];
  }
  if (Array.isArray(value)) {
    return value.flatMap(flattenSearchValues);
  }
  if (typeof value === "object") {
    return Object.values(value as Record<string, unknown>).flatMap(flattenSearchValues);
  }
  return [];
};

export const matchesAdminSearch = (item: unknown, query: string) => {
  const keyword = query.trim().toLowerCase();
  if (!keyword) return true;
  return flattenSearchValues(item).some((value) => value.toLowerCase().includes(keyword));
};

export const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) return error.message;
  if (typeof error === "object" && error && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string" && message.trim()) return message;
  }
  return fallback;
};
