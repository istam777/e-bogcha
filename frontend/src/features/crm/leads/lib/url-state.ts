import type { LeadSearchParams } from '@/shared/types/api';
import type { LeadStatus, LeadSource, OwnerState } from '@/shared/types/api';
import {
  localDateToStartOfDayInstant,
  localDateToEndOfDayExclusiveInstant,
} from './dates';

export function paramsToSearchParams(params: LeadSearchParams): URLSearchParams {
  const sp = new URLSearchParams();

  if (params.q) sp.set('q', params.q);
  if (params.branchId) sp.set('branchId', params.branchId);
  if (params.status) sp.set('status', params.status);
  if (params.source) sp.set('source', params.source);
  if (params.ownerOperatorId) sp.set('ownerOperatorId', params.ownerOperatorId);
  if (params.ownerState && params.ownerState !== 'ALL') sp.set('ownerState', params.ownerState);
  if (params.overdueOnly) sp.set('overdueOnly', 'true');
  if (params.createdFrom) sp.set('createdFrom', params.createdFrom);
  if (params.createdTo) sp.set('createdTo', params.createdTo);
  if (params.page !== undefined && params.page > 0) sp.set('page', String(params.page));
  if (params.size !== undefined && params.size !== 20) sp.set('size', String(params.size));

  return sp;
}

export function searchParamsToParams(sp: URLSearchParams): LeadSearchParams {
  const params: LeadSearchParams = {};

  const q = sp.get('q');
  if (q) params.q = q;

  const branchId = sp.get('branchId');
  if (branchId) params.branchId = branchId;

  const status = sp.get('status');
  if (status) params.status = status as LeadStatus;

  const source = sp.get('source');
  if (source) params.source = source as LeadSource;

  const ownerOperatorId = sp.get('ownerOperatorId');
  if (ownerOperatorId) params.ownerOperatorId = ownerOperatorId;

  const ownerState = sp.get('ownerState');
  if (ownerState) params.ownerState = ownerState as OwnerState;

  const overdueOnly = sp.get('overdueOnly');
  if (overdueOnly === 'true') params.overdueOnly = true;

  const createdFrom = sp.get('createdFrom');
  if (createdFrom) params.createdFrom = createdFrom;

  const createdTo = sp.get('createdTo');
  if (createdTo) params.createdTo = createdTo;

  const page = sp.get('page');
  if (page) params.page = Math.max(0, parseInt(page, 10) || 0);

  const size = sp.get('size');
  if (size) {
    const parsed = parseInt(size, 10);
    if (parsed >= 1 && parsed <= 100) params.size = parsed;
  }

  return params;
}

export function paramsToLocalDates(params: LeadSearchParams): {
  createdFromDate: string;
  createdToDate: string;
} {
  return {
    createdFromDate: params.createdFrom ? localDateFromInstantForInput(params.createdFrom) : '',
    createdToDate: params.createdTo ? localDateFromInstantForInput(params.createdTo) : '',
  };
}

function localDateFromInstantForInput(isoString: string): string {
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  } catch {
    return '';
  }
}

export function localDateToApiInstant(
  localDate: string,
  boundary: 'start' | 'end',
): string | undefined {
  if (boundary === 'start') return localDateToStartOfDayInstant(localDate);
  return localDateToEndOfDayExclusiveInstant(localDate);
}
