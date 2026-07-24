import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/shared/ui/Button';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
  hasPrevious: boolean;
  onPageChange: (page: number) => void;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  hasNext,
  hasPrevious,
  onPageChange,
}: PaginationProps) {
  const displayPage = page + 1;

  return (
    <nav className="pagination" aria-label="Sahifalar bo'yicha navigatsiya">
      <div className="pagination__info">
        Jami {totalElements.toLocaleString('uz-UZ')} ta natija
      </div>

      <div className="pagination__controls">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={!hasPrevious}
          aria-label="Oldingi sahifa"
        >
          <ChevronLeft size={16} />
          Oldingi
        </Button>

        <span className="pagination__current" aria-current="page">
          {displayPage} / {totalPages || 1}
        </span>

        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={!hasNext}
          aria-label="Keyingi sahifa"
        >
          Keyingi
          <ChevronRight size={16} />
        </Button>
      </div>
    </nav>
  );
}
