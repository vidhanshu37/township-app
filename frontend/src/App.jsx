import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, ROLE_HOME, useAuth } from './auth.jsx';
import AppShell from './components/AppShell.jsx';
import Login from './pages/Login.jsx';
import Dues from './pages/resident/Dues.jsx';
import Ledger from './pages/resident/Ledger.jsx';
import Complaints from './pages/resident/Complaints.jsx';
import Receipt from './pages/resident/Receipt.jsx';

// Only lets the right role through; everyone else is sent to sign in or to their own home.
function RequireRole({ role }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <div className="splash">Checking your session</div>;
  if (status === 'out') return <Navigate to="/login" replace state={{ from: location }} />;
  if (user.role !== role) return <Navigate to={ROLE_HOME[user.role]} replace />;
  return <Outlet />;
}

function HomeRedirect() {
  const { status, user } = useAuth();
  if (status === 'loading') return <div className="splash">Checking your session</div>;
  return <Navigate to={status === 'in' ? ROLE_HOME[user.role] : '/login'} replace />;
}

function StaffHome({ title, children }) {
  return (
    <>
      <h1>{title}</h1>
      <p className="muted" style={{ marginTop: 8 }}>{children}</p>
    </>
  );
}

function NotFound() {
  return (
    <div className="splash">
      <div>
        <h1>That page doesn't exist</h1>
        <p style={{ marginTop: 12 }}><Link to="/">Go to your home screen</Link></p>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />

          <Route element={<RequireRole role="RESIDENT" />}>
            <Route element={<AppShell />}>
              <Route path="/resident" element={<Dues />} />
              <Route path="/resident/ledger" element={<Ledger />} />
              <Route path="/resident/complaints" element={<Complaints />} />
              <Route path="/resident/receipt/:paymentId" element={<Receipt />} />
            </Route>
          </Route>

          <Route element={<RequireRole role="HELP_DESK" />}>
            <Route element={<AppShell />}>
              <Route path="/helpdesk" element={
                <StaffHome title="Help desk">Statement upload, the review queue and complaints are built in the next steps.</StaffHome>
              } />
            </Route>
          </Route>

          <Route element={<RequireRole role="COMMITTEE" />}>
            <Route element={<AppShell />}>
              <Route path="/committee" element={
                <StaffHome title="Committee">The dashboard is built in a later step.</StaffHome>
              } />
            </Route>
          </Route>

          <Route path="/" element={<HomeRedirect />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
