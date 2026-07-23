const ACTOR_STORAGE_KEY = 'ebogcha_actor_user_id';

export function getStoredActorId(): string | null {
  try {
    return localStorage.getItem(ACTOR_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setStoredActorId(uuid: string): void {
  try {
    localStorage.setItem(ACTOR_STORAGE_KEY, uuid);
  } catch {
    // localStorage unavailable; silent
  }
}

export function clearStoredActorId(): void {
  try {
    localStorage.removeItem(ACTOR_STORAGE_KEY);
  } catch {
    // localStorage unavailable; silent
  }
}

export function isValidUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

export function resolveActorId(): string | null {
  const stored = getStoredActorId();
  if (stored && isValidUuid(stored)) return stored;

  const env = import.meta.env.VITE_ACTOR_USER_ID;
  if (env && isValidUuid(env)) return env;

  return null;
}

export function shortenUuid(uuid: string): string {
  return uuid.slice(0, 8) + '…';
}
