import { FileX, Filter } from 'lucide-react';
import { Button } from '@/shared/ui/Button';

interface EmptyStateProps {
  hasFilters: boolean;
  onClearFilters?: () => void;
}

export function EmptyState({ hasFilters, onClearFilters }: EmptyStateProps) {
  return (
    <div className="empty-state" role="status">
      <div className="empty-state__icon">
        <FileX size={48} />
      </div>
      <h3 className="empty-state__title">
        {hasFilters
          ? "Tanlangan filtrlar bo'yicha lead topilmadi"
          : "Hozircha leadlar mavjud emas"}
      </h3>
      {hasFilters && onClearFilters && (
        <Button variant="secondary" size="sm" onClick={onClearFilters}>
          <Filter size={14} />
          Filtrlarni tozalash
        </Button>
      )}
    </div>
  );
}
