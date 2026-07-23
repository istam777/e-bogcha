export type LeadStatus =
  | 'NEW'
  | 'CONTACTED'
  | 'TOUR_PLANNED'
  | 'SUCCESSFUL'
  | 'NO_SHOW'
  | 'LOST'
  | 'ARCHIVED';

export type LeadSource = 'SOCIAL_MEDIA' | 'PHONE' | 'WALK_IN';

export type OwnerState = 'ALL' | 'ASSIGNED' | 'UNASSIGNED';

export interface LeadListItem {
  id: string;
  organizationId: string;
  branchId: string;
  branchName: string;
  source: LeadSource;
  status: LeadStatus;
  parentOrGuardianName: string;
  displayPhone: string;
  ownerOperatorId: string | null;
  ownerDisplayName: string | null;
  createdAt: string;
  updatedAt: string;
  firstContactDueAt: string;
  overdue: boolean;
}

export interface LeadSearchResult {
  items: LeadListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
}

export interface LeadSearchParams {
  q?: string;
  branchId?: string;
  status?: LeadStatus;
  source?: LeadSource;
  ownerOperatorId?: string;
  ownerState?: OwnerState;
  overdueOnly?: boolean;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  timestamp: string;
}

export type ErrorCode =
  | 'CRM_ACTOR_INVALID'
  | 'CRM_BRANCH_ACCESS_DENIED'
  | 'CRM_REQUEST_INVALID'
  | 'CRM_INTERNAL_ERROR'
  | 'CRM_LEAD_NOT_FOUND'
  | 'CRM_LEAD_ALREADY_OWNED'
  | 'CRM_LEAD_DUPLICATE'
  | 'CRM_STATUS_TRANSITION_INVALID'
  | 'CRM_LOST_REASON_REQUIRED'
  | 'CRM_BRANCH_ORGANIZATION_INVALID'
  | 'CRM_REFERENCE_INVALID';
