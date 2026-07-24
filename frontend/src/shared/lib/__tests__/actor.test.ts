import { describe, it, expect, beforeEach } from 'vitest';
import {
  isValidUuid,
  shortenUuid,
  getStoredActorId,
  setStoredActorId,
  clearStoredActorId,
  resolveActorId,
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

describe('resolveActorId', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('resolves valid stored actor in development', () => {
    setStoredActorId('44444444-4444-4444-8444-444444444444');
    expect(resolveActorId(true, getStoredActorId(), '')).toBe(
      '44444444-4444-4444-8444-444444444444',
    );
  });

  it('falls back to env actor in development', () => {
    expect(resolveActorId(true, null, '55555555-5555-5555-8555-555555555555')).toBe(
      '55555555-5555-5555-8555-555555555555',
    );
  });

  it('returns null in development when no valid actor', () => {
    expect(resolveActorId(true, null, '')).toBeNull();
  });

  it('ignores stored actor in production', () => {
    setStoredActorId('44444444-4444-4444-8444-444444444444');
    expect(resolveActorId(false, getStoredActorId(), '')).toBeNull();
  });

  it('ignores env actor in production', () => {
    expect(resolveActorId(false, null, '55555555-5555-5555-8555-555555555555')).toBeNull();
  });

  it('ignores invalid stored actor', () => {
    setStoredActorId('not-a-uuid');
    expect(resolveActorId(true, getStoredActorId(), '')).toBeNull();
  });

  it('ignores invalid env actor', () => {
    expect(resolveActorId(true, null, 'not-a-uuid')).toBeNull();
  });
});
