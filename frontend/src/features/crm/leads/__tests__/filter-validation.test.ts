import { describe, it, expect } from 'vitest';
import { validateFilters, isQueryEnabled } from '../lib/filter-validation';

describe('validateFilters', () => {
  it('accepts empty params', () => {
    expect(validateFilters({}).valid).toBe(true);
  });

  it('accepts valid UUID branchId', () => {
    expect(
      validateFilters({ branchId: '44444444-4444-4444-8444-444444444444' }).valid,
    ).toBe(true);
  });

  it('rejects malformed branchId', () => {
    const result = validateFilters({ branchId: 'not-a-uuid' });
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('rejects malformed ownerOperatorId', () => {
    const result = validateFilters({ ownerOperatorId: 'bad' });
    expect(result.valid).toBe(false);
  });

  it('rejects ownerOperatorId + UNASSIGNED', () => {
    const result = validateFilters({
      ownerOperatorId: '44444444-4444-4444-8444-444444444444',
      ownerState: 'UNASSIGNED',
    });
    expect(result.valid).toBe(false);
  });

  it('rejects invalid createdFrom', () => {
    const result = validateFilters({ createdFrom: 'not-a-date' });
    expect(result.valid).toBe(false);
  });

  it('rejects invalid createdTo', () => {
    const result = validateFilters({ createdTo: '2026-13-01' });
    expect(result.valid).toBe(false);
  });

  it('rejects createdFrom after createdTo', () => {
    const result = validateFilters({
      createdFrom: '2026-07-25',
      createdTo: '2026-07-23',
    });
    expect(result.valid).toBe(false);
  });

  it('accepts valid date range', () => {
    const result = validateFilters({
      createdFrom: '2026-07-20',
      createdTo: '2026-07-25',
    });
    expect(result.valid).toBe(true);
  });

  it('rejects single-char query', () => {
    const result = validateFilters({ q: 'A' });
    expect(result.valid).toBe(false);
  });

  it('accepts two-char query', () => {
    const result = validateFilters({ q: 'Al' });
    expect(result.valid).toBe(true);
  });

  it('rejects negative page', () => {
    const result = validateFilters({ page: -1 });
    expect(result.valid).toBe(false);
  });

  it('rejects size out of range', () => {
    expect(validateFilters({ size: 0 }).valid).toBe(false);
    expect(validateFilters({ size: 101 }).valid).toBe(false);
  });

  it('accepts valid size', () => {
    expect(validateFilters({ size: 20 }).valid).toBe(true);
  });
});

describe('isQueryEnabled', () => {
  it('returns false when actor not configured', () => {
    expect(isQueryEnabled(false, {})).toBe(false);
  });

  it('returns true with valid actor and no filters', () => {
    expect(isQueryEnabled(true, {})).toBe(true);
  });

  it('returns false with invalid filters', () => {
    expect(isQueryEnabled(true, { branchId: 'bad' })).toBe(false);
  });

  it('returns true with valid actor and valid filters', () => {
    expect(
      isQueryEnabled(true, {
        branchId: '44444444-4444-4444-8444-444444444444',
        status: 'NEW',
      }),
    ).toBe(true);
  });
});
