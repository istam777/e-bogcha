import { createContext, useContext } from 'react';

export interface ActorContextValue {
  actorId: string;
  isConfigured: boolean;
  setActor: (uuid: string) => void;
  resetActor: () => void;
}

export const ActorContext = createContext<ActorContextValue | null>(null);

export function useActor(): ActorContextValue {
  const ctx = useContext(ActorContext);
  if (!ctx) throw new Error('useActor must be used within ActorProvider');
  return ctx;
}
