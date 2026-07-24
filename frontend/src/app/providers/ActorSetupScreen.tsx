import { useState } from 'react';
import { UserCog } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { isValidUuid } from '@/shared/lib/actor';

interface ActorSetupScreenProps {
  onActorSet: (uuid: string) => void;
}

export function ActorSetupScreen({ onActorSet }: ActorSetupScreenProps) {
  const [input, setInput] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = input.trim();

    if (!trimmed) {
      setError('UUID kiritilishi shart');
      return;
    }

    if (!isValidUuid(trimmed)) {
      setError('Noto\'g\'ri UUID formati. Namuna: 44444444-4444-4444-8444-444444444444');
      return;
    }

    onActorSet(trimmed);
  };

  return (
    <div className="actor-setup">
      <div className="actor-setup__card">
        <div className="actor-setup__icon">
          <UserCog size={48} />
        </div>

        <h1 className="actor-setup__title">
          Vaqtinchalik foydalanuvchini sozlash
        </h1>

        <p className="actor-setup__description">
          Bu vaqtinchalik ruxsat berish mexanizmi. Haqiqiy tizimga kirish emas.
          Kelajakda haqiqiy autentifikatsiya bilan almashtiriladi.
        </p>

        <form onSubmit={handleSubmit} className="actor-setup__form">
          <Input
            label="Foydalanuvchi UUID"
            placeholder="44444444-4444-4444-8444-444444444444"
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              setError('');
            }}
            error={error}
            autoFocus
          />

          <Button type="submit" size="lg" className="actor-setup__submit">
            Davom etish
          </Button>
        </form>
      </div>
    </div>
  );
}
