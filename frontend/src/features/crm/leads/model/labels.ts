import type { LeadStatus, LeadSource, OwnerState } from '@/shared/types/api';

export const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Yangi',
  CONTACTED: 'Aloqa qilindi',
  TOUR_PLANNED: 'Ekskursiya rejalashtirilgan',
  SUCCESSFUL: 'Muvaffaqiyatli',
  NO_SHOW: 'Kelmagan',
  LOST: "Yo'qotilgan",
  ARCHIVED: 'Arxiv',
};

export const SOURCE_LABELS: Record<LeadSource, string> = {
  SOCIAL_MEDIA: 'Ijtimoiy tarmoq',
  PHONE: 'Telefon',
  WALK_IN: 'Bevosita tashrif',
};

export const OWNER_STATE_LABELS: Record<OwnerState, string> = {
  ALL: 'Barchasi',
  ASSIGNED: 'Biriktirilgan',
  UNASSIGNED: 'Biriktirilmagan',
};

export const STATUS_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({ value: value as LeadStatus, label }));
export const SOURCE_OPTIONS = Object.entries(SOURCE_LABELS).map(([value, label]) => ({ value: value as LeadSource, label }));
export const OWNER_STATE_OPTIONS = Object.entries(OWNER_STATE_LABELS).map(([value, label]) => ({ value: value as OwnerState, label }));

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
