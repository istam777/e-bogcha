import { createBrowserRouter, Navigate } from 'react-router-dom';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { AuthenticatedLayout } from './layouts/AuthenticatedLayout';
import { LeadListPage } from '@/features/crm/leads/pages/LeadListPage';
import { NotFoundPage } from './pages/NotFoundPage';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <AuthenticatedLayout />,
    children: [
      { index: true, element: <Navigate to="/crm/leads" replace /> },
      { path: 'crm/leads', element: <LeadListPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
