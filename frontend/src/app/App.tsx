import { useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ActorProvider, useActor } from './providers/ActorProvider';
import { ActorSetupScreen } from './providers/ActorSetupScreen';
import { router } from './router';

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        refetchOnWindowFocus: false,
      },
    },
  });
}

function AppInner() {
  const { isConfigured, setActor } = useActor();

  if (!isConfigured) {
    return <ActorSetupScreen onActorSet={setActor} />;
  }

  return <RouterProvider router={router} />;
}

export function App() {
  const [queryClient] = useState(makeQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <ActorProvider>
        <AppInner />
      </ActorProvider>
    </QueryClientProvider>
  );
}
