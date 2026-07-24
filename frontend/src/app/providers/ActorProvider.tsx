import { useState, useCallback, useMemo, type ReactNode } from 'react';
import {
  resolveActorId,
  setStoredActorId,
  clearStoredActorId,
  getStoredActorId,
} from '@/shared/lib/actor';
import { config } from '@/shared/config/env';
import { ActorContext, type ActorContextValue } from './useActor';

export function ActorProvider({ children }: { children: ReactNode }) {
  const [resolvedId, setResolvedId] = useState(() =>
    resolveActorId(config.isDevelopment, getStoredActorId(), config.actorUserId),
  );

  const setActor = useCallback(
    (uuid: string) => {
      if (!config.isDevelopment) return;
      setStoredActorId(uuid);
      setResolvedId(uuid);
    },
    [],
  );

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
