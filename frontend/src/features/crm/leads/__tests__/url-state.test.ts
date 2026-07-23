import { describe, it, expect } from 'vitest';
import { paramsToSearchParams, searchParamsToParams } from '../lib/url-state';

describe('paramsToSearchParams', () => {
  it('serializes non-default params', () => {
    const sp = paramsToSearchParams({
      q: 'test',
      status: 'NEW',
      page: 2,
      size: 50,
    });
    expect(sp.get('q')).toBe('test');
    expect(sp.get('status')).toBe('NEW');
    expect(sp.get('page')).toBe('2');
    expect(sp.get('size')).toBe('50');
  });

  it('omits defaults', () => {
    const sp = paramsToSearchParams({
      page: 0,
      size: 20,
      ownerState: 'ALL',
    });
    expect(sp.has('page')).toBe(false);
    expect(sp.has('size')).toBe(false);
    expect(sp.has('ownerState')).toBe(false);
  });

  it('omits empty q', () => {
    const sp = paramsToSearchParams({ q: '' });
    expect(sp.has('q')).toBe(false);
  });
});

describe('searchParamsToParams', () => {
  it('parses URL search params', () => {
    const sp = new URLSearchParams('q=test&status=NEW&page=1&size=50');
    const params = searchParamsToParams(sp);
    expect(params.q).toBe('test');
    expect(params.status).toBe('NEW');
    expect(params.page).toBe(1);
    expect(params.size).toBe(50);
  });

  it('ignores invalid page', () => {
    const sp = new URLSearchParams('page=-1');
    const params = searchParamsToParams(sp);
    expect(params.page).toBe(0);
  });

  it('ignores invalid size', () => {
    const sp = new URLSearchParams('size=101');
    const params = searchParamsToParams(sp);
    expect(params.size).toBeUndefined();
  });

  it('parses overdueOnly', () => {
    const sp = new URLSearchParams('overdueOnly=true');
    const params = searchParamsToParams(sp);
    expect(params.overdueOnly).toBe(true);
  });
});
