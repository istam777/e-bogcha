import { useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ActorProvider } from './providers/ActorProvider';
import { createAppRouter } from './router';

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

export function App() {
  const [queryClient] = useState(makeQueryClient);
  const [router] = useState(createAppRouter);

  return (
    <QueryClientProvider client={queryClient}>
      <ActorProvider>
        <RouterProvider router={router} />
      </ActorProvider>
    </QueryClientProvider>
  );
}
