import { Phone, AlertTriangle } from 'lucide-react';
import { StatusBadge } from '@/shared/ui/Badge';
import { formatInstant } from '../lib/dates';
import type { LeadListItem } from '@/shared/types/api';

interface LeadCardsProps {
  leads: LeadListItem[];
}

export function LeadCards({ leads }: LeadCardsProps) {
  return (
    <div className="lead-cards" aria-label="Leadlar ro'yxati">
      {leads.map((lead) => (
        <LeadCard key={lead.id} lead={lead} />
      ))}
    </div>
  );
}

function LeadCard({ lead }: { lead: LeadListItem }) {
  return (
    <article className={`lead-card ${lead.overdue ? 'lead-card--overdue' : ''}`}>
      <div className="lead-card__header">
        <h3 className="lead-card__name">
          {lead.parentOrGuardianName}
          {lead.overdue && (
            <span className="lead-card__overdue" aria-label="Kechikkan">
              <AlertTriangle size={14} />
            </span>
          )}
        </h3>
        <StatusBadge status={lead.status} />
      </div>

      <div className="lead-card__details">
        <div className="lead-card__row">
          <Phone size={14} aria-hidden="true" />
          <span>{lead.displayPhone}</span>
        </div>

        <div className="lead-card__row">
          <span className="lead-card__label">Filial:</span>
          <span>{lead.branchName}</span>
        </div>

        <div className="lead-card__row">
          <span className="lead-card__label">Mas'ul:</span>
          <span>{lead.ownerDisplayName || 'Biriktirilmagan'}</span>
        </div>

        <div className="lead-card__row">
          <span className="lead-card__label">Aloqa muddati:</span>
          <span className={lead.overdue ? 'text-danger' : ''}>
            {formatInstant(lead.firstContactDueAt)}
          </span>
        </div>
      </div>
    </article>
  );
}
