import type { LeadSearchParams } from '@/shared/types/api';
import { isValidUuid } from '@/shared/lib/actor';
import { isValidDateString } from './dates';

const VALID_STATUSES = new Set<string>([
  'NEW', 'CONTACTED', 'TOUR_PLANNED', 'SUCCESSFUL', 'NO_SHOW', 'LOST', 'ARCHIVED',
]);
const VALID_SOURCES = new Set<string>(['SOCIAL_MEDIA', 'PHONE', 'WALK_IN']);
const VALID_OWNER_STATES = new Set<string>(['ALL', 'ASSIGNED', 'UNASSIGNED']);

export interface FilterValidationResult {
  valid: boolean;
  errors: string[];
}

export function validateFilters(params: LeadSearchParams): FilterValidationResult {
  const errors: string[] = [];

  if (params.branchId && !isValidUuid(params.branchId)) {
    errors.push('branchId: noto\'g\'ri UUID formati');
  }

  if (params.ownerOperatorId && !isValidUuid(params.ownerOperatorId)) {
    errors.push('ownerOperatorId: noto\'g\'ri UUID formati');
  }

  if (params.ownerOperatorId && params.ownerState === 'UNASSIGNED') {
    errors.push('ownerOperatorId va UNASSIGNED birga ishlatilmaydi');
  }

  if (params.status && !VALID_STATUSES.has(params.status)) {
    errors.push('status: noto\'g\'ri enum qiymati');
  }

  if (params.source && !VALID_SOURCES.has(params.source)) {
    errors.push('source: noto\'g\'ri enum qiymati');
  }

  if (params.ownerState && !VALID_OWNER_STATES.has(params.ownerState)) {
    errors.push('ownerState: noto\'g\'ri enum qiymati');
  }

  if (params.createdFrom && !isValidDateString(params.createdFrom)) {
    errors.push('createdFrom: noto\'g\'ri sana formati');
  }

  if (params.createdTo && !isValidDateString(params.createdTo)) {
    errors.push('createdTo: noto\'g\'ri sana formati');
  }

  if (
    params.createdFrom &&
    params.createdTo &&
    isValidDateString(params.createdFrom) &&
    isValidDateString(params.createdTo) &&
    params.createdFrom > params.createdTo
  ) {
    errors.push('createdFrom createdTo dan katta bo\'lishi mumkin emas');
  }

  if (params.page !== undefined && params.page < 0) {
    errors.push('page: manfiy qiymat');
  }

  if (params.size !== undefined && (params.size < 1 || params.size > 100)) {
    errors.push('size: 1-100 oralig\'ida bo\'lishi kerak');
  }

  if (params.q && params.q.trim().length === 1) {
    errors.push('q: kamida 2 ta belgi kerak');
  }

  return { valid: errors.length === 0, errors };
}

export function isQueryEnabled(
  actorConfigured: boolean,
  params: LeadSearchParams,
): boolean {
  if (!actorConfigured) return false;
  return validateFilters(params).valid;
}
