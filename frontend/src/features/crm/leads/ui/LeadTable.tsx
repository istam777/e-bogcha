import { Phone, Smartphone, Users, AlertTriangle } from 'lucide-react';
import { StatusBadge } from '@/shared/ui/Badge';
import { SOURCE_LABELS } from '../model/labels';
import { formatInstant } from '../lib/dates';
import type { LeadListItem } from '@/shared/types/api';

const SOURCE_ICONS: Record<string, typeof Phone> = {
  PHONE: Phone,
  SOCIAL_MEDIA: Smartphone,
  WALK_IN: Users,
};

interface LeadTableProps {
  leads: LeadListItem[];
}

export function LeadTable({ leads }: LeadTableProps) {
  return (
    <div className="table-wrapper">
      <table className="lead-table" aria-label="Leadlar ro'yxati">
        <thead>
          <tr>
            <th scope="col">Ota-ona / vasiy</th>
            <th scope="col">Telefon</th>
            <th scope="col">Manba</th>
            <th scope="col">Holat</th>
            <th scope="col">Filial</th>
            <th scope="col">Mas'ul operator</th>
            <th scope="col">Yaratilgan vaqt</th>
            <th scope="col">Aloqa muddati</th>
          </tr>
        </thead>
        <tbody>
          {leads.map((lead) => (
            <LeadRow key={lead.id} lead={lead} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LeadRow({ lead }: { lead: LeadListItem }) {
  const SourceIcon = SOURCE_ICONS[lead.source] || Phone;

  return (
    <tr className={`lead-row ${lead.overdue ? 'lead-row--overdue' : ''}`}>
      <td>
        <div className="lead-row__name">
          {lead.parentOrGuardianName}
          {lead.overdue && (
            <span className="lead-row__overdue-badge" aria-label="Kechikkan">
              <AlertTriangle size={14} />
            </span>
          )}
        </div>
      </td>
      <td className="lead-row__phone">{lead.displayPhone}</td>
      <td>
        <div className="lead-row__source">
          <SourceIcon size={14} aria-hidden="true" />
          {SOURCE_LABELS[lead.source]}
        </div>
      </td>
      <td><StatusBadge status={lead.status} /></td>
      <td>{lead.branchName}</td>
      <td>{lead.ownerDisplayName || <span className="text-muted">Biriktirilmagan</span>}</td>
      <td>{formatInstant(lead.createdAt)}</td>
      <td>
        <span className={lead.overdue ? 'text-danger' : ''}>
          {formatInstant(lead.firstContactDueAt)}
        </span>
      </td>
    </tr>
  );
}
