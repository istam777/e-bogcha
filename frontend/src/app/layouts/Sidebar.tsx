import { NavLink } from 'react-router-dom';
import {
  Home,
  Users,
  GraduationCap,
  FileText,
  DollarSign,
  CalendarCheck,
  Settings,
  X,
} from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

interface SidebarItem {
  label: string;
  path: string;
  icon: typeof Home;
  disabled?: boolean;
}

const NAV_ITEMS: SidebarItem[] = [
  { label: 'Bosh sahifa', path: '/', icon: Home, disabled: true },
  { label: 'CRM Leadlar', path: '/crm/leads', icon: Users },
  { label: 'Shartnomalar', path: '#', icon: GraduationCap, disabled: true },
  { label: 'Moliya', path: '#', icon: DollarSign, disabled: true },
  { label: 'Davomat', path: '#', icon: CalendarCheck, disabled: true },
  { label: 'Xodimlar', path: '#', icon: FileText, disabled: true },
  { label: 'Sozlamalar', path: '#', icon: Settings, disabled: true },
];

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  return (
    <>
      {isOpen && (
        <div className="sidebar-overlay" onClick={onClose} aria-hidden="true" />
      )}
      <aside
        className={`sidebar ${isOpen ? 'sidebar--open' : ''}`}
        aria-label="Asosiy menyu"
      >
        <div className="sidebar__brand">
          <img
            src="/branding/oxu-kids-logo.png"
            alt="Oxu Kids logo"
            className="sidebar__logo"
          />
          <div className="sidebar__brand-text">
            <span className="sidebar__brand-name">OXU KIDS CRM</span>
            <span className="sidebar__brand-sub">e-bog'cha boshqaruv tizimi</span>
          </div>
        </div>

        <button
          className="sidebar__close"
          onClick={onClose}
          aria-label="Menyuni yopish"
        >
          <X size={20} />
        </button>

        <nav className="sidebar__nav">
          {NAV_ITEMS.map((item) => (
            <SidebarLink key={item.label} item={item} onClick={onClose} />
          ))}
        </nav>

        <div className="sidebar__footer">
          <img
            src="/branding/oxu-kids-logo.png"
            alt=""
            className="sidebar__footer-logo"
            aria-hidden="true"
          />
          <p className="sidebar__footer-text">
            Premium ta'lim muhitida baxtli bolalik sari!
          </p>
        </div>
      </aside>
    </>
  );
}

function SidebarLink({ item, onClick }: { item: SidebarItem; onClick: () => void }) {
  if (item.disabled) {
    return (
      <div className="sidebar__link sidebar__link--disabled" aria-disabled="true">
        <item.icon size={18} aria-hidden="true" />
        <span>{item.label}</span>
      </div>
    );
  }

  return (
    <NavLink
      to={item.path}
      end={item.path === '/'}
      className={({ isActive }) =>
        `sidebar__link ${isActive ? 'sidebar__link--active' : ''}`
      }
      onClick={onClick}
    >
      <item.icon size={18} aria-hidden="true" />
      <span>{item.label}</span>
    </NavLink>
  );
}
