import { describe, it, expect } from 'vitest';
import { ApiError } from '../client';
import type { ProblemDetail } from '@/shared/types/api';

describe('ApiError', () => {
  it('creates error from ProblemDetail', () => {
    const problem: ProblemDetail = {
      type: 'urn:problem:crm-actor-invalid',
      title: 'Invalid CRM actor',
      status: 400,
      detail: 'X-Actor-User-Id must contain a valid UUID.',
      instance: '/api/v1/crm/leads',
      code: 'CRM_ACTOR_INVALID',
      timestamp: '2026-07-23T12:00:00Z',
    };

    const error = new ApiError(problem);
    expect(error.name).toBe('ApiError');
    expect(error.status).toBe(400);
    expect(error.code).toBe('CRM_ACTOR_INVALID');
    expect(error.title).toBe('Invalid CRM actor');
    expect(error.detail).toBe('X-Actor-User-Id must contain a valid UUID.');
    expect(error.message).toBe('Invalid CRM actor');
  });
});
