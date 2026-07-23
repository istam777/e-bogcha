import { useId } from 'react';

interface SwitchProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

export function Switch({ label, checked, onChange }: SwitchProps) {
  const id = useId();

  return (
    <div className="switch-group">
      <button
        id={id}
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className={`switch ${checked ? 'switch--on' : ''}`}
        onClick={() => onChange(!checked)}
        type="button"
      >
        <span className="switch__thumb" />
      </button>
      <label htmlFor={id} className="switch-label">
        {label}
      </label>
    </div>
  );
}
