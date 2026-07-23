import { AlertTriangle, RefreshCw, UserCog } from 'lucide-react';
import { Button } from './Button';
import { ApiError } from '@/shared/api/client';

interface ErrorDisplayProps {
  error: ApiError | Error;
  onRetry?: () => void;
  onActorReset?: () => void;
}

export function ErrorDisplay({ error, onRetry, onActorReset }: ErrorDisplayProps) {
  const isApiError = error instanceof ApiError;

  const title = isApiError ? error.title : 'Xatolik yuz berdi';
  const detail = isApiError ? error.detail : error.message || 'Kutilmagan xatolik.';
  const code = isApiError ? error.code : 'UNKNOWN';

  return (
    <div className="error-display" role="alert">
      <div className="error-display__icon">
        <AlertTriangle size={32} />
      </div>
      <h3 className="error-display__title">{title}</h3>
      <p className="error-display__detail">{detail}</p>
      <p className="error-display__code">Kod: {code}</p>
      <div className="error-display__actions">
        {code === 'CRM_ACTOR_INVALID' && onActorReset && (
          <Button onClick={onActorReset} variant="secondary" size="sm">
            <UserCog size={16} />
            Foydalanuvchini almashtirish
          </Button>
        )}
        {onRetry && code !== 'CRM_ACTOR_INVALID' && (
          <Button onClick={onRetry} variant="secondary" size="sm">
            <RefreshCw size={16} />
            Qayta urinish
          </Button>
        )}
      </div>
    </div>
  );
}
