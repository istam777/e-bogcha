import { describe, it, expect, beforeEach } from 'vitest';
import {
  isValidUuid,
  shortenUuid,
  getStoredActorId,
  setStoredActorId,
  clearStoredActorId,
} from '../actor';

describe('isValidUuid', () => {
  it('accepts valid UUID', () => {
    expect(isValidUuid('44444444-4444-4444-8444-444444444444')).toBe(true);
  });

  it('accepts uppercase UUID', () => {
    expect(isValidUuid('44444444-4444-4444-8444-444444444444'.toUpperCase())).toBe(true);
  });

  it('rejects invalid format', () => {
    expect(isValidUuid('not-a-uuid')).toBe(false);
    expect(isValidUuid('')).toBe(false);
    expect(isValidUuid('44444444')).toBe(false);
  });
});

describe('shortenUuid', () => {
  it('shortens a UUID', () => {
    expect(shortenUuid('44444444-4444-4444-8444-444444444444')).toBe('44444444…');
  });
});

describe('localStorage actor functions', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('stores and retrieves actor id', () => {
    setStoredActorId('44444444-4444-4444-8444-444444444444');
    expect(getStoredActorId()).toBe('44444444-4444-4444-8444-444444444444');
  });

  it('clears actor id', () => {
    setStoredActorId('44444444-4444-4444-8444-444444444444');
    clearStoredActorId();
    expect(getStoredActorId()).toBeNull();
  });

  it('returns null when nothing stored', () => {
    expect(getStoredActorId()).toBeNull();
  });
});
