const YYYY_MM_DD = /^\d{4}-\d{2}-\d{2}$/;

function isValidLocalDate(localDate: string): boolean {
  if (!YYYY_MM_DD.test(localDate)) return false;
  const [year, month, day] = localDate.split('-').map(Number);
  if (month < 1 || month > 12) return false;
  if (day < 1 || day > 31) return false;
  const date = new Date(year, month - 1, day);
  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
  );
}

export function formatInstant(isoString: string | null | undefined): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '—';
    return new Intl.DateTimeFormat('uz-UZ', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date);
  } catch {
    return '—';
  }
}

export function formatShortDate(isoString: string | null | undefined): string {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '—';
    return new Intl.DateTimeFormat('uz-UZ', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(date);
  } catch {
    return '—';
  }
}

export function localDateToStartOfDayInstant(localDate: string): string | undefined {
  if (!localDate || !isValidLocalDate(localDate)) return undefined;
  const [year, month, day] = localDate.split('-').map(Number);
  const date = new Date(year, month - 1, day, 0, 0, 0, 0);
  if (isNaN(date.getTime())) return undefined;
  return date.toISOString();
}

export function localDateToEndOfDayExclusiveInstant(localDate: string): string | undefined {
  if (!localDate || !isValidLocalDate(localDate)) return undefined;
  const [year, month, day] = localDate.split('-').map(Number);
  const date = new Date(year, month - 1, day + 1, 0, 0, 0, 0);
  if (isNaN(date.getTime())) return undefined;
  return date.toISOString();
}

export function localDateFromInstant(isoString: string | null | undefined): string {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  } catch {
    return '';
  }
}

export function isValidDateString(value: string): boolean {
  return isValidLocalDate(value);
}
