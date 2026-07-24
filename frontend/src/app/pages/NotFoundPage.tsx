import { Link } from 'react-router-dom';
import { FileQuestion } from 'lucide-react';
import { Button } from '@/shared/ui/Button';

export function NotFoundPage() {
  return (
    <div className="not-found">
      <div className="not-found__icon">
        <FileQuestion size={64} />
      </div>
      <h1 className="not-found__title">404</h1>
      <p className="not-found__detail">Sahifa topilmadi</p>
      <Link to="/">
        <Button>Bosh sahifaga qaytish</Button>
      </Link>
    </div>
  );
}
