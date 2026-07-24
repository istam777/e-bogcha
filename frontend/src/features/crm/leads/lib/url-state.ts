import type { LeadSearchParams } from '@/shared/types/api';
import {
  localDateToStartOfDayInstant,
  localDateToEndOfDayExclusiveInstant,
} from './dates';

const VALID_STATUSES = new Set<string>([
  'NEW', 'CONTACTED', 'TOUR_PLANNED', 'SUCCESSFUL', 'NO_SHOW', 'LOST', 'ARCHIVED',
]);
const VALID_SOURCES = new Set<string>(['SOCIAL_MEDIA', 'PHONE', 'WALK_IN']);
const VALID_OWNER_STATES = new Set<string>(['ALL', 'ASSIGNED', 'UNASSIGNED']);

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
  if (status && VALID_STATUSES.has(status)) params.status = status as LeadSearchParams['status'];

  const source = sp.get('source');
  if (source && VALID_SOURCES.has(source)) params.source = source as LeadSearchParams['source'];

  const ownerOperatorId = sp.get('ownerOperatorId');
  if (ownerOperatorId) params.ownerOperatorId = ownerOperatorId;

  const ownerState = sp.get('ownerState');
  if (ownerState && VALID_OWNER_STATES.has(ownerState))
    params.ownerState = ownerState as LeadSearchParams['ownerState'];

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

export function localDateToApiInstant(
  localDate: string,
  boundary: 'start' | 'end',
): string | undefined {
  if (localDate.includes('T')) return undefined;
  if (boundary === 'start') return localDateToStartOfDayInstant(localDate);
  return localDateToEndOfDayExclusiveInstant(localDate);
}
