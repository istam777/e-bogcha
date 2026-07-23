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
  { label: 'Bosh sahifa', path: '/', icon: Home },
  {
    label: 'CRM',
    path: '/crm/leads',
    icon: Users,
    disabled: false,
  },
  { label: 'Qabul / Shartnoma', path: '#', icon: GraduationCap, disabled: true },
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
        <div className="sidebar__header">
          <div className="sidebar__brand">
            <span className="sidebar__brand-name">E-Bog'cha</span>
            <span className="sidebar__brand-sub">Oxu Kids</span>
          </div>
          <button
            className="sidebar__close"
            onClick={onClose}
            aria-label="Menyuni yopish"
          >
            <X size={20} />
          </button>
        </div>

        <nav className="sidebar__nav">
          {NAV_ITEMS.map((item) => (
            <SidebarLink key={item.label} item={item} onClick={onClose} />
          ))}
        </nav>

        <div className="sidebar__footer">
          <span className="sidebar__version">v0.0.1</span>
        </div>
      </aside>
    </>
  );
}

function SidebarLink({ item, onClick }: { item: SidebarItem; onClick: () => void }) {
  if (item.disabled) {
    return (
      <div className="sidebar__link sidebar__link--disabled" title="Keyingi bosqichda">
        <item.icon size={18} aria-hidden="true" />
        <span>{item.label}</span>
        <span className="sidebar__badge">Keyingi bosqichda</span>
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
