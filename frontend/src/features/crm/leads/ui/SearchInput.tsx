import { useState, useCallback, useRef, useEffect } from 'react';
import { Search, X } from 'lucide-react';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  debounceMs?: number;
}

export function SearchInput({ value, onChange, debounceMs = 350 }: SearchInputProps) {
  const [draft, setDraft] = useState(value);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastCommittedRef = useRef(value);

  useEffect(() => {
    if (value !== lastCommittedRef.current) {
      setDraft(value);
      lastCommittedRef.current = value;
    }
  }, [value]);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  const commit = useCallback(
    (next: string) => {
      if (timerRef.current) clearTimeout(timerRef.current);
      const trimmed = next.trim();
      if (trimmed.length === 1) {
        lastCommittedRef.current = '';
        onChange('');
        return;
      }
      if (trimmed.length >= 2 && trimmed.length <= 100) {
        timerRef.current = setTimeout(() => {
          lastCommittedRef.current = trimmed;
          onChange(trimmed);
        }, debounceMs);
      } else if (trimmed.length === 0) {
        lastCommittedRef.current = '';
        onChange('');
      }
    },
    [onChange, debounceMs],
  );

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = e.target.value;
      setDraft(newValue);
      commit(newValue);
    },
    [commit],
  );

  const handleClear = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setDraft('');
    lastCommittedRef.current = '';
    onChange('');
  }, [onChange]);

  const trimmedDraft = draft.trim();
  const showError = trimmedDraft.length === 1;

  return (
    <div className="search-input-wrapper">
      <Search size={18} className="search-input-icon" aria-hidden="true" />
      <input
        type="text"
        value={draft}
        onChange={handleChange}
        placeholder="Ota-ona ismi yoki telefon bo'yicha qidirish"
        className="search-input"
        aria-label="Qidirish"
        aria-describedby={showError ? 'search-error' : undefined}
        aria-invalid={showError}
      />
      {draft && (
        <button
          type="button"
          onClick={handleClear}
          className="search-input-clear"
          aria-label="Qidiruvni tozalash"
        >
          <X size={16} />
        </button>
      )}
      {showError && (
        <span id="search-error" className="search-input-error" role="alert">
          Qidiruv kamida 2 ta belgidan iborat bo'lishi kerak
        </span>
      )}
    </div>
  );
}
