import { useState, useCallback, type ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

interface AppLayoutProps {
  children: ReactNode;
}

export function AppLayout({ children }: AppLayoutProps) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleMenuClick = useCallback(() => setSidebarOpen(true), []);
  const handleClose = useCallback(() => setSidebarOpen(false), []);

  return (
    <div className="app-layout">
      <Sidebar isOpen={sidebarOpen} onClose={handleClose} />

      <div className="app-layout__main">
        <Header onMenuClick={handleMenuClick} />
        <main className="app-layout__content">
          {children}
        </main>
      </div>
    </div>
  );
}
