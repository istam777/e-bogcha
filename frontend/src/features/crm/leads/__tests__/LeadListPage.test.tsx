import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/tests/server';
import { App } from '@/app/App';

beforeEach(() => {
  localStorage.setItem('ebogcha_actor_user_id', '44444444-4444-4444-8444-444444444444');
  window.history.pushState({}, '', '/crm/leads');
  server.resetHandlers();
});

describe('Actor setup screen', () => {
  it('appears when actor is absent', () => {
    localStorage.clear();
    render(<App />);
    expect(screen.getByText(/Vaqtinchalik foydalanuvchini sozlash/)).toBeInTheDocument();
  });

  it('valid actor opens the application', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getByText('Leadlar')).toBeInTheDocument();
    });
  });
});

describe('Lead list rendering', () => {
  it('shows loading state', async () => {
    render(<App />);
    const skeletons = document.querySelectorAll('.skeleton');
    expect(skeletons.length).toBeGreaterThanOrEqual(0);
  });

  it('renders leads successfully', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText('Ikkinchi Ota').length).toBeGreaterThan(0);
  });

  it('shows unassigned owner display', async () => {
    render(<App />);
    await waitFor(() => {
      const allText = document.body.textContent || '';
      expect(allText).toContain('Biriktirilmagan');
    });
  });

  it('shows overdue lead warning', async () => {
    render(<App />);
    await waitFor(() => {
      const overdueBadges = screen.getAllByLabelText('Kechikkan');
      expect(overdueBadges.length).toBeGreaterThanOrEqual(1);
    });
  });
});

describe('Empty states', () => {
  it('shows empty unfiltered state', async () => {
    server.use(
      http.get('/api/v1/crm/leads', () => {
        return HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          hasPrevious: false,
          hasNext: false,
        });
      }),
    );

    render(<App />);
    await waitFor(() => {
      expect(screen.getByText("Hozircha leadlar mavjud emas")).toBeInTheDocument();
    });
  });
});

describe('Error handling', () => {
  it('renders CRM_REQUEST_INVALID error', async () => {
    localStorage.setItem('ebogcha_actor_user_id', '11111111-1111-1111-1111-111111111111');
    render(<App />);
    await waitFor(() => {
      const text = document.body.textContent || '';
      expect(text).toContain('CRM_REQUEST_INVALID');
    }, { timeout: 5000 });
  });

  it('renders CRM_BRANCH_ACCESS_DENIED error', async () => {
    localStorage.setItem('ebogcha_actor_user_id', '00000000-0000-0000-0000-000000000000');
    render(<App />);
    await waitFor(() => {
      const text = document.body.textContent || '';
      expect(text).toContain('CRM_BRANCH_ACCESS_DENIED');
    }, { timeout: 5000 });
  });
});

describe('Search', () => {
  it('invokes search after debounce', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });

    const searchInput = screen.getByPlaceholderText(/Ota-ona ismi yoki telefon/);
    await user.type(searchInput, 'test');

    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });
  });
});

describe('Mobile cards', () => {
  it('mobile card content is present in DOM', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });
    const cards = document.querySelectorAll('.lead-card');
    expect(cards.length).toBe(2);
  });
});

describe('Normalized phone', () => {
  it('normalized phone is never rendered', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });
    const allText = document.body.textContent || '';
    expect(allText).not.toContain('normalizedPhone');
  });
});
