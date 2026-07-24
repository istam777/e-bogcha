import type { LeadStatus } from '@/shared/types/api';
import { STATUS_LABELS } from '@/features/crm/leads/model/labels';

interface BadgeProps {
  status: LeadStatus;
  className?: string;
}

const STATUS_VARIANTS: Record<LeadStatus, string> = {
  NEW: 'badge--info',
  CONTACTED: 'badge--primary',
  TOUR_PLANNED: 'badge--warning',
  SUCCESSFUL: 'badge--success',
  NO_SHOW: 'badge--danger',
  LOST: 'badge--danger',
  ARCHIVED: 'badge--muted',
};

export function StatusBadge({ status, className = '' }: BadgeProps) {
  return (
    <span
      className={`badge ${STATUS_VARIANTS[status]} ${className}`}
      aria-label={`Holat: ${STATUS_LABELS[status]}`}
    >
      <span className="badge__dot" aria-hidden="true" />
      {STATUS_LABELS[status]}
    </span>
  );
}
