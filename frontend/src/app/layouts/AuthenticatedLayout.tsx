import { Navigate, Outlet } from 'react-router-dom';
import { useActor } from '@/app/providers/useActor';
import { AppLayout } from './AppLayout';

export function AuthenticatedLayout() {
  const { isConfigured } = useActor();

  if (!isConfigured) {
    return <Navigate to="/login" replace />;
  }

  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  );
}
