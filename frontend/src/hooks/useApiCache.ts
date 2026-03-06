const cache = new Map<string, unknown>()

export function setApiCache(key: string, value: unknown) {
  cache.set(key, value)
}

export function getApiCache<T>(key: string): T | undefined {
  return cache.get(key) as T | undefined
}
