import { http, HttpResponse } from 'msw';
import type { LeadSearchResult } from '@/shared/types/api';

const sampleLeads: LeadSearchResult = {
  items: [
    {
      id: '11111111-1111-4111-8111-111111111111',
      organizationId: '22222222-2222-4222-8222-222222222222',
      branchId: '33333333-3333-4333-8333-333333333333',
      branchName: 'Markaziy filial',
      source: 'PHONE',
      status: 'NEW',
      parentOrGuardianName: 'Test Ota',
      displayPhone: '+998 90 123 45 67',
      ownerOperatorId: null,
      ownerDisplayName: null,
      createdAt: '2026-07-23T08:00:00Z',
      updatedAt: '2026-07-23T08:00:00Z',
      firstContactDueAt: '2026-07-24T08:00:00Z',
      overdue: false,
    },
    {
      id: '22222222-2222-4222-8222-222222222222',
      organizationId: '22222222-2222-4222-8222-222222222222',
      branchId: '33333333-3333-4333-8333-333333333333',
      branchName: 'Markaziy filial',
      source: 'SOCIAL_MEDIA',
      status: 'CONTACTED',
      parentOrGuardianName: 'Ikkinchi Ota',
      displayPhone: '+998 91 234 56 78',
      ownerOperatorId: '44444444-4444-4444-8444-444444444444',
      ownerDisplayName: 'Test Operator',
      createdAt: '2026-07-22T10:00:00Z',
      updatedAt: '2026-07-23T09:00:00Z',
      firstContactDueAt: '2026-07-23T06:00:00Z',
      overdue: true,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  hasPrevious: false,
  hasNext: false,
};

const actorInvalidProblem = {
  type: 'urn:problem:crm-actor-invalid',
  title: 'Invalid CRM actor',
  status: 400,
  detail: 'X-Actor-User-Id must contain a valid UUID.',
  instance: '/api/v1/crm/leads',
  code: 'CRM_ACTOR_INVALID',
  timestamp: '2026-07-23T12:00:00Z',
};

const branchAccessProblem = {
  type: 'urn:problem:crm-branch-access-denied',
  title: 'CRM branch access denied',
  status: 403,
  detail: 'The actor does not have access to this lead\'s branch.',
  instance: '/api/v1/crm/leads',
  code: 'CRM_BRANCH_ACCESS_DENIED',
  timestamp: '2026-07-23T12:00:00Z',
};

const requestInvalidProblem = {
  type: 'urn:problem:crm-request-invalid',
  title: 'Invalid CRM request',
  status: 400,
  detail: 'The request body, path, or parameters are invalid.',
  instance: '/api/v1/crm/leads',
  code: 'CRM_REQUEST_INVALID',
  timestamp: '2026-07-23T12:00:00Z',
};

export const handlers = [
  http.get('/api/v1/crm/leads', ({ request }) => {
    const url = new URL(request.url);
    const actorId = request.headers.get('X-Actor-User-Id');

    if (!actorId) {
      return HttpResponse.json(actorInvalidProblem, { status: 400 });
    }

    if (actorId === '00000000-0000-0000-0000-000000000000') {
      return HttpResponse.json(branchAccessProblem, {
        status: 403,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    }

    if (actorId === '11111111-1111-1111-1111-111111111111') {
      return HttpResponse.json(requestInvalidProblem, {
        status: 400,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    }

    const status = url.searchParams.get('status');
    const source = url.searchParams.get('source');
    const q = url.searchParams.get('q');

    let items = [...sampleLeads.items];

    if (status) {
      items = items.filter((item) => item.status === status);
    }
    if (source) {
      items = items.filter((item) => item.source === source);
    }
    if (q && q.length >= 2) {
      const lowerQ = q.toLowerCase();
      items = items.filter(
        (item) =>
          item.parentOrGuardianName.toLowerCase().includes(lowerQ) ||
          item.displayPhone.replace(/\s/g, '').includes(lowerQ.replace(/\s/g, '')),
      );
    }

    return HttpResponse.json({
      ...sampleLeads,
      items,
      totalElements: items.length,
      totalPages: items.length === 0 ? 0 : 1,
    });
  }),
];
