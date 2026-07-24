import { describe, it, expect } from 'vitest';
import { serializeSearchParams, hasActiveFilters, countActiveFilters } from '../api/query-params';

describe('serializeSearchParams', () => {
  it('omits default values', () => {
    const result = serializeSearchParams({
      q: '',
      ownerState: 'ALL',
      page: 0,
      size: 20,
    });
    expect(result).toEqual({});
  });

  it('includes non-default values', () => {
    const result = serializeSearchParams({
      q: 'test',
      status: 'NEW',
      source: 'PHONE',
      ownerState: 'ASSIGNED',
      overdueOnly: true,
      page: 2,
      size: 50,
    });
    expect(result).toEqual({
      q: 'test',
      status: 'NEW',
      source: 'PHONE',
      ownerState: 'ASSIGNED',
      overdueOnly: 'true',
      page: 2,
      size: 50,
    });
  });

  it('trims q value', () => {
    const result = serializeSearchParams({ q: '  test  ' });
    expect(result.q).toBe('test');
  });

  it('omits page when 0', () => {
    const result = serializeSearchParams({ page: 0 });
    expect(result.page).toBeUndefined();
  });

  it('omits size when 20 (default)', () => {
    const result = serializeSearchParams({ size: 20 });
    expect(result.size).toBeUndefined();
  });

  it('includes size when not 20', () => {
    const result = serializeSearchParams({ size: 10 });
    expect(result.size).toBe(10);
  });
});

describe('hasActiveFilters', () => {
  it('returns false for empty params', () => {
    expect(hasActiveFilters({})).toBe(false);
  });

  it('returns true when q is set', () => {
    expect(hasActiveFilters({ q: 'test' })).toBe(true);
  });

  it('returns true when status is set', () => {
    expect(hasActiveFilters({ status: 'NEW' })).toBe(true);
  });

  it('returns true when overdueOnly is true', () => {
    expect(hasActiveFilters({ overdueOnly: true })).toBe(true);
  });

  it('returns false when ownerState is ALL', () => {
    expect(hasActiveFilters({ ownerState: 'ALL' })).toBe(false);
  });

  it('returns true when ownerState is ASSIGNED', () => {
    expect(hasActiveFilters({ ownerState: 'ASSIGNED' })).toBe(true);
  });
});

describe('countActiveFilters', () => {
  it('counts active filters', () => {
    const count = countActiveFilters({
      q: 'test',
      status: 'NEW',
      overdueOnly: true,
    });
    expect(count).toBe(3);
  });

  it('returns 0 for defaults', () => {
    expect(countActiveFilters({ ownerState: 'ALL' })).toBe(0);
  });
});
