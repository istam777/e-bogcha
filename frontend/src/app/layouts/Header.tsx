import { Menu, LogOut } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { shortenUuid } from '@/shared/lib/actor';
import { useActor } from '@/app/providers/useActor';

interface HeaderProps {
  onMenuClick: () => void;
}

export function Header({ onMenuClick }: HeaderProps) {
  const { actorId, resetActor } = useActor();

  return (
    <header className="app-header">
      <div className="app-header__left">
        <Button
          variant="ghost"
          size="sm"
          onClick={onMenuClick}
          aria-label="Menyuni ochish"
          className="app-header__menu-btn"
        >
          <Menu size={20} />
        </Button>
      </div>

      <div className="app-header__right">
        <div className="app-header__actor">
          <span className="app-header__actor-label">Foydalanuvchi:</span>
          <span className="app-header__actor-id" title={actorId}>
            {shortenUuid(actorId)}
          </span>
        </div>

        <Button
          variant="ghost"
          size="sm"
          onClick={resetActor}
          aria-label="Foydalanuvchini almashtirish"
          title="Foydalanuvchini almashtirish"
        >
          <LogOut size={16} />
        </Button>
      </div>
    </header>
  );
}
