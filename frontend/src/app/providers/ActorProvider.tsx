import { useState, useCallback, useMemo, type ReactNode } from 'react';
import { resolveActorId, setStoredActorId, clearStoredActorId } from '@/shared/lib/actor';
import { ActorContext, type ActorContextValue } from './useActor';

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

  const value = useMemo<ActorContextValue>(
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
