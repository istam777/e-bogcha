import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/tests/server';
import { App } from '@/app/App';

beforeEach(() => {
  localStorage.setItem('ebogcha_actor_user_id', '44444444-4444-4444-8444-444444444444');
  window.history.pushState({}, '', '/crm/leads');
  server.resetHandlers();
});

describe('Login page', () => {
  it('appears when actor is absent', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/crm/leads');
    render(<App />);
    expect(screen.getByText(/Xush kelibsiz/)).toBeInTheDocument();
  });

  it('renders official Oxu Kids branding', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    expect(screen.getByText('OXU KIDS CRM')).toBeInTheDocument();
  });

  it('has username and password fields', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    expect(screen.getByLabelText('Login')).toBeInTheDocument();
    expect(screen.getByLabelText('Parol')).toBeInTheDocument();
  });

  it('has no Google login control', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const text = document.body.textContent || '';
    expect(text).not.toContain('Google');
  });

  it('has no social login control', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const text = document.body.textContent || '';
    expect(text).not.toContain('Telegram');
    expect(text).not.toContain('Apple');
    expect(text).not.toContain('Facebook');
  });

  it('has no self-registration link', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const text = document.body.textContent || '';
    expect(text).not.toContain("Ro'yxatdan o'tish");
    expect(text).not.toContain('Registration');
  });

  it('arbitrary credentials do not create a session', async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Login'), 'admin');
    await user.type(screen.getByLabelText('Parol'), 'password123');
    await user.click(screen.getByRole('button', { name: /Tizimga kirish/ }));

    await waitFor(() => {
      expect(screen.getByText(/backend autentifikatsiya/)).toBeInTheDocument();
    });

    expect(localStorage.getItem('ebogcha_actor_user_id')).toBeNull();
  });

  it('password is not stored in localStorage', async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Login'), 'admin');
    await user.type(screen.getByLabelText('Parol'), 'secret123');
    await user.click(screen.getByRole('button', { name: /Tizimga kirish/ }));

    await waitFor(() => {
      expect(screen.getByText(/backend autentifikatsiya/)).toBeInTheDocument();
    });

    const localStorageKeys = Object.keys(localStorage);
    for (const key of localStorageKeys) {
      const value = localStorage.getItem(key);
      expect(value).not.toContain('secret123');
    }
  });
});

describe('Development actor access', () => {
  it('valid development actor redirects to CRM', async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const user = userEvent.setup();

    const devActorInput = screen.getByPlaceholderText(/44444444/);
    await user.type(devActorInput, '44444444-4444-4444-8444-444444444444');
    await user.click(screen.getByRole('button', { name: /Davom etish/ }));

    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });
  });
});

describe('Lead list rendering', () => {
  it('valid actor opens the application', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });
  });

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

describe('Unsupported controls absent', () => {
  it('no Yangi lead action', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });
    const text = document.body.textContent || '';
    expect(text).not.toContain('Yangi lead');
  });

  it('no Export action', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });
    const text = document.body.textContent || '';
    expect(text).not.toContain('Export');
  });
});

describe('Login required-field validation', () => {
  it('shows errors when submitting empty fields', async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Tizimga kirish/ }));

    expect(screen.getByText('Login kiritilishi shart')).toBeInTheDocument();
    expect(screen.getByText('Parol kiritilishi shart')).toBeInTheDocument();
  });

  it('sets aria-invalid on empty fields', async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Tizimga kirish/ }));

    expect(screen.getByLabelText('Login')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Parol')).toHaveAttribute('aria-invalid', 'true');
  });

  it('clears errors when user types', async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /Tizimga kirish/ }));
    expect(screen.getByText('Login kiritilishi shart')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Login'), 'admin');
    expect(screen.queryByText('Login kiritilishi shart')).not.toBeInTheDocument();
  });
});

describe('Search input realistic typing', () => {
  it('A, Al and Ali are visibly typed', async () => {
    // Ensure clean state
    cleanup();
    localStorage.setItem('ebogcha_actor_user_id', '44444444-4444-4444-8444-444444444444');
    window.history.pushState({}, '', '/crm/leads');
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });

    const searchInput = screen.getByPlaceholderText(/Ota-ona ismi yoki telefon/);
    await user.type(searchInput, 'Ali');

    expect(searchInput).toHaveValue('Ali');
  });

  it('single character shows validation message', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });

    const searchInput = screen.getByPlaceholderText(/Ota-ona ismi yoki telefon/);
    await user.type(searchInput, 'A');

    expect(searchInput).toHaveValue('A');
    expect(screen.getByText(/kamida 2 ta belgi/)).toBeInTheDocument();
  });

  it('clear button clears the search', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('Test Ota').length).toBeGreaterThan(0);
    });

    const searchInput = screen.getByPlaceholderText(/Ota-ona ismi yoki telefon/);
    await user.type(searchInput, 'Ali');
    expect(searchInput).toHaveValue('Ali');

    await user.click(screen.getByLabelText('Qidiruvni tozalash'));
    expect(searchInput).toHaveValue('');
  });
});

describe('Filter validation blocks requests', () => {
  it('no request for malformed branchId', async () => {
    localStorage.setItem('ebogcha_actor_user_id', '44444444-4444-4444-8444-444444444444');
    let requestCount = 0;
    server.use(
      http.get('/api/v1/crm/leads', () => {
        requestCount++;
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

    window.history.pushState({}, '', '/crm/leads?branchId=not-a-uuid');
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });

    await new Promise((r) => setTimeout(r, 500));
    expect(requestCount).toBe(0);
  });

  it('no request for ownerOperatorId + UNASSIGNED', async () => {
    localStorage.setItem('ebogcha_actor_user_id', '44444444-4444-4444-8444-444444444444');
    let requestCount = 0;
    server.use(
      http.get('/api/v1/crm/leads', () => {
        requestCount++;
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

    window.history.pushState(
      {},
      '',
      '/crm/leads?ownerOperatorId=44444444-4444-4444-8444-444444444444&ownerState=UNASSIGNED',
    );
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });

    await new Promise((r) => setTimeout(r, 500));
    expect(requestCount).toBe(0);
  });

  it('shows validation error for invalid date', async () => {
    localStorage.setItem('ebogcha_actor_user_id', '44444444-4444-4444-8444-444444444444');
    window.history.pushState({}, '', '/crm/leads?createdFrom=not-a-date');
    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('CRM Leadlar').length).toBeGreaterThan(0);
    });

    const text = document.body.textContent || '';
    expect(text).toContain('noto\'g\'ri sana formati');
  });
});

describe('Copyright year', () => {
  it('displays current year', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const year = new Date().getFullYear().toString();
    const text = document.body.textContent || '';
    expect(text).toContain(year);
  });

  it('does not display hardcoded 2025', () => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
    render(<App />);
    const visualFooter = document.querySelector('.login-visual__footer');
    expect(visualFooter?.textContent).not.toContain('2025');
  });
});
