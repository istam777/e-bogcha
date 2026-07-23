import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from './layouts/AppLayout';
import { LeadListPage } from '@/features/crm/leads/pages/LeadListPage';
import { NotFoundPage } from './pages/NotFoundPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/crm/leads" replace /> },
      { path: 'crm/leads', element: <LeadListPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
