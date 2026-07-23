import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './shared/styles/global.css';
import './shared/ui/ui.css';
import './shared/ui/switch.css';
import './shared/ui/error-display.css';
import './app/layouts/sidebar.css';
import './app/layouts/header.css';
import './app/layouts/layout.css';
import './app/pages/not-found.css';
import './app/providers/actor-setup.css';
import './features/crm/leads/ui/search-input.css';
import './features/crm/leads/ui/filter-bar.css';
import './features/crm/leads/ui/lead-table.css';
import './features/crm/leads/ui/lead-cards.css';
import './features/crm/leads/ui/pagination.css';
import './features/crm/leads/ui/empty-state.css';
import './features/crm/leads/pages/lead-list-page.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
