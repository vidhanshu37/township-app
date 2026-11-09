import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import Nameplate from './Nameplate.jsx';

const ROLE_LABEL = { RESIDENT: 'Resident', HELP_DESK: 'Help desk', COMMITTEE: 'Committee' };

// Screens are added to these lists as they are built.
const NAV = {
  RESIDENT: [
    { to: '/resident', label: 'Dues', end: true },
    { to: '/resident/ledger', label: 'Ledger' },
    { to: '/resident/complaints', label: 'Complaints' },
  ],
  HELP_DESK: [
    { to: '/helpdesk/statements', label: 'Statements' },
    { to: '/helpdesk/payments', label: 'Payments' },
    { to: '/helpdesk/billing', label: 'Billing' },
    { to: '/helpdesk/complaints', label: 'Complaints' },
  ],
  COMMITTEE: [],
};

export default function AppShell() {
  const { user, signOut } = useAuth();
  const { pathname } = useLocation();
  const items = NAV[user.role] ?? [];
  // the Dues screen already shows the big nameplate, so the small one would only repeat it
  const showSmallPlate = user.role === 'RESIDENT' && user.flatNo && pathname !== '/resident';

  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <header className="chrome">
        <div className="topbar">
          <div className="topbar-id">
            {showSmallPlate ? <Nameplate flatNo={user.flatNo} size="sm" /> : null}
            <div className="who">
              <strong>{user.name}</strong>
              <span>{ROLE_LABEL[user.role]}</span>
            </div>
          </div>
          <button type="button" className="btn btn-quiet" onClick={signOut}>Sign out</button>
        </div>
        {items.length > 1 ? (
          <nav className="tabs" aria-label="Main">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className="tab">{item.label}</NavLink>
            ))}
          </nav>
        ) : null}
      </header>
      <main id="main" className={user.role === 'COMMITTEE' ? 'page page-dash' : 'page'}>
        <Outlet />
      </main>
    </>
  );
}
