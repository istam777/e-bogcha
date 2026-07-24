import { apiGet } from '@/shared/api/client';
import type { LeadSearchResult, LeadSearchParams } from '@/shared/types/api';
import { serializeSearchParams } from './query-params';

const LEADS_PATH = '/api/v1/crm/leads';

export async function fetchLeads(
  params: LeadSearchParams,
  actorId: string,
  signal?: AbortSignal,
): Promise<LeadSearchResult> {
  const serialized = serializeSearchParams(params);
  return apiGet<LeadSearchResult>(LEADS_PATH, serialized, { actorId, signal });
}
