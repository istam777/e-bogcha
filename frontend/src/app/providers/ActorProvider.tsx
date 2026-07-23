import { createContext, useContext, useState, useCallback, useMemo, type ReactNode } from 'react';
import { resolveActorId, setStoredActorId, clearStoredActorId } from '@/shared/lib/actor';

interface ActorContextValue {
  actorId: string;
  isConfigured: boolean;
  setActor: (uuid: string) => void;
  resetActor: () => void;
}

const ActorContext = createContext<ActorContextValue | null>(null);

export function ActorProvider({ children }: { children: ReactNode }) {
  const [resolvedId, setResolvedId] = useState(() => resolveActorId());

  const setActor = useCallback((uuid: string) => {
    setStoredActorId(uuid);
    setResolvedId(uuid);
  }, []);

  const resetActor = useCallback(() => {
    clearStoredActorId();
    setResolvedId(null);
  }, []);

  const value = useMemo(
    () => ({
      actorId: resolvedId || '',
      isConfigured: !!resolvedId,
      setActor,
      resetActor,
    }),
    [resolvedId, setActor, resetActor],
  );

  return <ActorContext.Provider value={value}>{children}</ActorContext.Provider>;
}

export function useActor(): ActorContextValue {
  const ctx = useContext(ActorContext);
  if (!ctx) throw new Error('useActor must be used within ActorProvider');
  return ctx;
}
