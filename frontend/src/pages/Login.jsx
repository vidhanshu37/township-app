import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { api } from '../api.js';
import { ROLE_HOME, useAuth } from '../auth.jsx';
import { Notice } from '../components/ui.jsx';

const DEMO_ACCOUNTS = [
  { phone: '9000000001', label: 'Neeraj Kulkarni', detail: 'Resident, T07-1203, bills paid' },
  { phone: '9000000002', label: 'Swati Joshi', detail: 'Resident, T12-0408, overdue' },
  { phone: '9000000003', label: 'Aamir Shaikh', detail: 'Resident, T21-1601, part paid' },
  { phone: '9000000010', label: 'Help desk', detail: 'Bills, bank statements, complaints' },
  { phone: '9000000020', label: 'Committee', detail: 'Dashboard' },
];
const HIDE_DEMO = import.meta.env.VITE_HIDE_DEMO_ACCOUNTS === 'true';
const RESEND_SECONDS = 30;

export default function Login() {
  const { status, user, notice, signIn } = useAuth();
  const [step, setStep] = useState('phone');
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [demoOtp, setDemoOtp] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [wait, setWait] = useState(0);

  useEffect(() => {
    if (wait <= 0) return undefined;
    const timer = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(timer);
  }, [wait]);

  if (status === 'in') return <Navigate to={ROLE_HOME[user.role]} replace />;
  if (status === 'loading') return <div className="splash">Checking your session</div>;

  async function sendCode(event) {
    event?.preventDefault();
    if (!/^\d{10}$/.test(phone)) {
      setError('Enter your 10-digit mobile number.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api('/api/auth/request-otp', { method: 'POST', body: { phone }, auth: false });
      setDemoOtp(res?.demoOtp ?? null);
      setOtp('');
      setStep('otp');
      setWait(RESEND_SECONDS);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function verify(event) {
    event.preventDefault();
    if (!/^\d{6}$/.test(otp)) {
      setError('Enter the 6-digit code.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api('/api/auth/verify-otp', { method: 'POST', body: { phone, otp }, auth: false });
      await signIn(res); // once signed in, this screen redirects to the right home
    } catch (err) {
      setError(err.status === 401
        ? 'That code is wrong or has expired. Check it, or ask for a new code.'
        : err.message);
      setBusy(false);
    }
  }

  function changeNumber() {
    setStep('phone');
    setError(null);
    setDemoOtp(null);
    setOtp('');
  }

  return (
    <div className="login">
      <div className="login-panel">
        <h1 className="wordmark">Township</h1>
        <p className="login-sub">Sign in with your registered mobile number.</p>

        {notice ? <Notice kind="info">{notice}</Notice> : null}

        <div className="sheet">
          {step === 'phone' ? (
            <form onSubmit={sendCode} noValidate>
              <h2>Mobile number</h2>
              {error ? <Notice kind="error">{error}</Notice> : null}
              <div className="field">
                <label htmlFor="phone">10-digit mobile number</label>
                <input
                  id="phone" name="phone" type="tel" inputMode="numeric" autoComplete="tel-national"
                  value={phone} onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                />
                <span className="hint">Use the number the township office has on file.</span>
              </div>
              <button type="submit" className="btn btn-block" disabled={busy}>
                {busy ? 'Sending code' : 'Send code'}
              </button>

              {HIDE_DEMO ? null : (
                <details className="demo">
                  <summary>Demo accounts</summary>
                  <ul className="demo-list">
                    {DEMO_ACCOUNTS.map((a) => (
                      <li key={a.phone}>
                        <button type="button" className="demo-item" onClick={() => setPhone(a.phone)}>
                          <strong>{a.label}</strong>
                          <span>{a.detail}</span>
                          <span className="num">{a.phone}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </details>
              )}
            </form>
          ) : (
            <form onSubmit={verify} noValidate>
              <h2>Enter your code</h2>
              <p className="muted" style={{ marginBottom: 16 }}>
                If <span className="num">{phone}</span> is registered, a 6-digit code is on its way.
              </p>
              {demoOtp ? (
                <Notice kind="info">
                  Demo mode is on. Your code is <strong className="num">{demoOtp}</strong>.
                  <button type="button" className="btn btn-link" onClick={() => setOtp(demoOtp)}>Use this code</button>
                </Notice>
              ) : null}
              {error ? <Notice kind="error">{error}</Notice> : null}
              <div className="field">
                <label htmlFor="otp">6-digit code</label>
                <input
                  id="otp" name="otp" className="code" type="text" inputMode="numeric"
                  autoComplete="one-time-code" value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                />
              </div>
              <button type="submit" className="btn btn-block" disabled={busy}>
                {busy ? 'Signing in' : 'Sign in'}
              </button>
              <div className="actions">
                <button type="button" className="btn btn-link" onClick={changeNumber}>Change number</button>
                <button type="button" className="btn btn-link" onClick={sendCode} disabled={busy || wait > 0}>
                  {wait > 0 ? `Send a new code in ${wait}s` : 'Send a new code'}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
