import { describe, it, expect } from 'vitest';
import { STATUS_LABELS, SOURCE_LABELS, OWNER_STATE_LABELS } from '../model/labels';

describe('STATUS_LABELS', () => {
  it('maps all statuses to Uzbek labels', () => {
    expect(STATUS_LABELS.NEW).toBe('Yangi');
    expect(STATUS_LABELS.CONTACTED).toBe('Aloqa qilindi');
    expect(STATUS_LABELS.TOUR_PLANNED).toBe('Ekskursiya rejalashtirilgan');
    expect(STATUS_LABELS.SUCCESSFUL).toBe('Muvaffaqiyatli');
    expect(STATUS_LABELS.NO_SHOW).toBe('Kelmagan');
    expect(STATUS_LABELS.LOST).toBe("Yo'qotilgan");
    expect(STATUS_LABELS.ARCHIVED).toBe('Arxiv');
  });
});

describe('SOURCE_LABELS', () => {
  it('maps all sources to Uzbek labels', () => {
    expect(SOURCE_LABELS.SOCIAL_MEDIA).toBe('Ijtimoiy tarmoq');
    expect(SOURCE_LABELS.PHONE).toBe('Telefon');
    expect(SOURCE_LABELS.WALK_IN).toBe('Bevosita tashrif');
  });
});

describe('OWNER_STATE_LABELS', () => {
  it('maps all owner states to Uzbek labels', () => {
    expect(OWNER_STATE_LABELS.ALL).toBe('Barchasi');
    expect(OWNER_STATE_LABELS.ASSIGNED).toBe('Biriktirilgan');
    expect(OWNER_STATE_LABELS.UNASSIGNED).toBe('Biriktirilmagan');
  });
});
