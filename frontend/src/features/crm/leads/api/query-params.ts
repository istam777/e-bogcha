import type { LeadSearchParams } from '@/shared/types/api';

const BOOLEAN_TRUE = 'true';

export function serializeSearchParams(params: LeadSearchParams): Record<string, string | number> {
  const serialized: Record<string, string | number> = {};

  if (params.q && params.q.trim()) {
    serialized.q = params.q.trim();
  }
  if (params.branchId) {
    serialized.branchId = params.branchId;
  }
  if (params.status) {
    serialized.status = params.status;
  }
  if (params.source) {
    serialized.source = params.source;
  }
  if (params.ownerOperatorId) {
    serialized.ownerOperatorId = params.ownerOperatorId;
  }
  if (params.ownerState && params.ownerState !== 'ALL') {
    serialized.ownerState = params.ownerState;
  }
  if (params.overdueOnly) {
    serialized.overdueOnly = BOOLEAN_TRUE;
  }
  if (params.createdFrom) {
    serialized.createdFrom = params.createdFrom;
  }
  if (params.createdTo) {
    serialized.createdTo = params.createdTo;
  }
  if (params.page !== undefined && params.page > 0) {
    serialized.page = params.page;
  }
  if (params.size !== undefined && params.size !== 20) {
    serialized.size = params.size;
  }

  return serialized;
}

export function buildDefaultParams(): LeadSearchParams {
  return {
    q: '',
    status: undefined,
    source: undefined,
    ownerState: 'ALL',
    overdueOnly: false,
    page: 0,
    size: 20,
  };
}

export function hasActiveFilters(params: LeadSearchParams): boolean {
  return !!(
    params.q ||
    params.branchId ||
    params.status ||
    params.source ||
    params.ownerOperatorId ||
    (params.ownerState && params.ownerState !== 'ALL') ||
    params.overdueOnly ||
    params.createdFrom ||
    params.createdTo
  );
}

export function countActiveFilters(params: LeadSearchParams): number {
  let count = 0;
  if (params.q) count++;
  if (params.branchId) count++;
  if (params.status) count++;
  if (params.source) count++;
  if (params.ownerOperatorId) count++;
  if (params.ownerState && params.ownerState !== 'ALL') count++;
  if (params.overdueOnly) count++;
  if (params.createdFrom) count++;
  if (params.createdTo) count++;
  return count;
}
