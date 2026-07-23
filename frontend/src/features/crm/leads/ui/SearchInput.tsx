import { useCallback } from 'react';
import { Search, X } from 'lucide-react';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
}

export function SearchInput({ value, onChange }: SearchInputProps) {
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = e.target.value;
      if (newValue.length < 2 && newValue.length > 0) {
        return;
      }
      onChange(newValue);
    },
    [onChange],
  );

  const handleClear = useCallback(() => {
    onChange('');
  }, [onChange]);

  const showError = value.length === 1;

  return (
    <div className="search-input-wrapper">
      <Search size={18} className="search-input-icon" aria-hidden="true" />
      <input
        type="text"
        value={value}
        onChange={handleChange}
        placeholder="Ota-ona ismi yoki telefon bo'yicha qidirish"
        className="search-input"
        aria-label="Qidirish"
        aria-describedby={showError ? 'search-error' : undefined}
        aria-invalid={showError}
      />
      {value && (
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
