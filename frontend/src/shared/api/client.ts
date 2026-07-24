import { config } from '@/shared/config/env';
import type { ProblemDetail } from '@/shared/types/api';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string;
  readonly detail: string;
  readonly instance: string;
  readonly timestamp: string;

  constructor(problem: ProblemDetail) {
    super(problem.title);
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code;
    this.title = problem.title;
    this.detail = problem.detail;
    this.instance = problem.instance;
    this.timestamp = problem.timestamp;
  }
}

function buildUrl(path: string, params?: Record<string, string | number | boolean | undefined | null>): string {
  const base = config.apiBaseUrl;
  const url = new URL(path, base || window.location.origin);

  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }
  }

  return url.toString();
}

async function parseError(response: Response): Promise<never> {
  const contentType = response.headers.get('content-type') || '';

  if (contentType.includes('application/problem+json')) {
    const problem: ProblemDetail = await response.json();
    throw new ApiError(problem);
  }

  throw new ApiError({
    type: 'urn:problem:unknown',
    title: 'Request failed',
    status: response.status,
    detail: 'An unexpected error occurred.',
    instance: response.url,
    code: 'CRM_INTERNAL_ERROR',
    timestamp: new Date().toISOString(),
  });
}

export interface RequestOptions {
  signal?: AbortSignal;
  headers?: Record<string, string>;
  actorId?: string;
}

export async function apiGet<T>(
  path: string,
  params?: Record<string, string | number | boolean | undefined | null>,
  options?: RequestOptions,
): Promise<T> {
  const url = buildUrl(path, params);
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options?.headers,
  };

  if (options?.actorId) {
    headers['X-Actor-User-Id'] = options.actorId;
  }

  const response = await fetch(url, {
    method: 'GET',
    headers,
    signal: options?.signal,
  });

  if (!response.ok) {
    await parseError(response);
  }

  return response.json() as Promise<T>;
}
