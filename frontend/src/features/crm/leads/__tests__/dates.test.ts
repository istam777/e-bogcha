import { describe, it, expect } from 'vitest';
import {
  formatInstant,
  localDateToStartOfDayInstant,
  localDateToEndOfDayExclusiveInstant,
  localDateFromInstant,
} from '../lib/dates';

describe('formatInstant', () => {
  it('formats a valid ISO string', () => {
    const result = formatInstant('2026-07-23T08:00:00Z');
    expect(result).not.toBe('—');
    expect(result.length).toBeGreaterThan(0);
  });

  it('returns em dash for null', () => {
    expect(formatInstant(null)).toBe('—');
  });

  it('returns em dash for undefined', () => {
    expect(formatInstant(undefined)).toBe('—');
  });

  it('returns em dash for invalid string', () => {
    expect(formatInstant('not-a-date')).toBe('—');
  });
});

describe('localDateToStartOfDayInstant', () => {
  it('converts local date to start of day ISO string', () => {
    const result = localDateToStartOfDayInstant('2026-07-23');
    expect(result).toBeDefined();
    const date = new Date(result!);
    expect(date.getHours()).toBe(0);
    expect(date.getMinutes()).toBe(0);
    expect(date.getSeconds()).toBe(0);
  });

  it('returns undefined for empty string', () => {
    expect(localDateToStartOfDayInstant('')).toBeUndefined();
  });
});

describe('localDateToEndOfDayExclusiveInstant', () => {
  it('converts local date to start of next day', () => {
    const result = localDateToEndOfDayExclusiveInstant('2026-07-23');
    expect(result).toBeDefined();
    const date = new Date(result!);
    expect(date.getHours()).toBe(0);
    expect(date.getMinutes()).toBe(0);
    expect(date.getSeconds()).toBe(0);
    expect(date.getDate()).toBe(24);
  });

  it('returns undefined for empty string', () => {
    expect(localDateToEndOfDayExclusiveInstant('')).toBeUndefined();
  });
});

describe('localDateFromInstant', () => {
  it('extracts date portion from ISO string', () => {
    const result = localDateFromInstant('2026-07-23T08:00:00Z');
    expect(result).toBe('2026-07-23');
  });

  it('returns empty for null', () => {
    expect(localDateFromInstant(null)).toBe('');
  });

  it('returns empty for invalid', () => {
    expect(localDateFromInstant('invalid')).toBe('');
  });
});
