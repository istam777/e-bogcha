import { describe, it, expect } from 'vitest';
import {
  formatInstant,
  localDateToStartOfDayInstant,
  localDateToEndOfDayExclusiveInstant,
  localDateFromInstant,
  isValidDateString,
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

  it('returns undefined for invalid format', () => {
    expect(localDateToStartOfDayInstant('not-a-date')).toBeUndefined();
  });

  it('returns undefined for invalid calendar date', () => {
    expect(localDateToStartOfDayInstant('2026-02-30')).toBeUndefined();
  });

  it('returns undefined for Feb 29 in non-leap year', () => {
    expect(localDateToStartOfDayInstant('2025-02-29')).toBeUndefined();
  });

  it('accepts Feb 29 in leap year', () => {
    expect(localDateToStartOfDayInstant('2024-02-29')).toBeDefined();
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

  it('returns undefined for invalid format', () => {
    expect(localDateToEndOfDayExclusiveInstant('2026-13-01')).toBeUndefined();
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

describe('isValidDateString', () => {
  it('accepts valid YYYY-MM-DD', () => {
    expect(isValidDateString('2026-07-23')).toBe(true);
  });

  it('accepts leap year date', () => {
    expect(isValidDateString('2024-02-29')).toBe(true);
  });

  it('rejects non-leap year Feb 29', () => {
    expect(isValidDateString('2025-02-29')).toBe(false);
  });

  it('rejects invalid month', () => {
    expect(isValidDateString('2026-13-01')).toBe(false);
  });

  it('rejects invalid day', () => {
    expect(isValidDateString('2026-02-30')).toBe(false);
  });

  it('rejects wrong format', () => {
    expect(isValidDateString('07/23/2026')).toBe(false);
    expect(isValidDateString('2026-7-23')).toBe(false);
    expect(isValidDateString('not-a-date')).toBe(false);
    expect(isValidDateString('')).toBe(false);
  });
});
