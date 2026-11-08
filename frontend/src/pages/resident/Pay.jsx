import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { Notice } from '../../components/ui.jsx';
import { formatMoney } from '../../format.js';

// Demo payment: create an order, then ask the backend to simulate the gateway confirming it.
export default function Pay({ balance, onPaid }) {
  const [amount, setAmount] = useState(String(balance.toFixed(2)));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(null);

  const value = Number(amount);
  const valid = Number.isFinite(value) && value >= 1 && /^\d+(\.\d{1,2})?$/.test(amount.trim());

  async function submit(e) {
    e.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    setError('');
    try {
      const order = await api('/api/resident/payments/orders', { method: 'POST', body: { amount: value } });
      const result = await api(`/api/resident/payments/orders/${encodeURIComponent(order.orderId)}/simulate`, { method: 'POST' });
      setDone({ ...result, amount: order.amount ?? value });
      onPaid();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <Notice kind="ok">
        <strong>Payment of {formatMoney(done.amount)} received.</strong>
        {done.message ? <div className="hint">{done.message}</div> : null}
        {done.paymentId ? (
          <Link className="btn btn-link" to={`/resident/receipt/${encodeURIComponent(done.paymentId)}`}>View receipt</Link>
        ) : null}
        <button type="button" className="btn btn-link" onClick={() => setDone(null)}>Make another payment</button>
      </Notice>
    );
  }

  return (
    <form className="sheet pay" onSubmit={submit} noValidate>
      <h2>Pay maintenance</h2>
      {error ? <Notice kind="error">{error}</Notice> : null}
      <div className="field">
        <label htmlFor="amount">Amount in rupees</label>
        <input id="amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} aria-describedby="amount-hint" />
        <span id="amount-hint" className="hint">
          Payments clear your oldest bills first. This is a demo payment, no real money moves.
        </span>
      </div>
      <button type="submit" className="btn btn-block" disabled={!valid || busy}>
        {busy ? 'Paying' : valid ? `Pay ${formatMoney(value)}` : 'Enter an amount of ₹1 or more'}
      </button>
    </form>
  );
}
