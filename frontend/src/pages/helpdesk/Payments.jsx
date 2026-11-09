import { useState } from 'react';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDateTime, formatMoney } from '../../format.js';
import { useLoad } from '../../useLoad.js';

function PaymentRow({ p, onChanged }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const reversed = p.status === 'REVERSED';

  async function reverse() {
    setBusy(true);
    setError('');
    try {
      await api(`/api/helpdesk/payments/${encodeURIComponent(p.paymentId)}/reverse`, { method: 'POST', body: { reason: reason.trim() } });
      setOpen(false);
      setReason('');
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="txn">
      <div className="txn-head">
        <div>
          <div className="row-title num">{formatMoney(p.amount)} <span className="row-sub">Flat {p.flatNo}</span></div>
          <div className="row-sub">{formatDateTime(p.paidAt)} · {p.source === 'BANK' ? 'Bank statement' : 'Online payment'}</div>
          <div className="row-sub num">{p.paymentId}</div>
        </div>
        <div className="row-side">
          {reversed ? <span className="chip chip-overdue">Reversed</span> : <span className="chip chip-paid">Received</span>}
        </div>
      </div>
      {!reversed && !open ? (
        <div className="txn-actions"><button type="button" className="btn btn-quiet" onClick={() => setOpen(true)}>Reverse payment</button></div>
      ) : null}
      {open ? (
        <div className="txn-actions">
          {error ? <Notice kind="error">{error}</Notice> : null}
          <div className="field">
            <label htmlFor={`rev-${p.paymentId}`}>Why is this payment being reversed?</label>
            <input id={`rev-${p.paymentId}`} value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
            <span className="hint">The bills it paid become due again. A reversal cannot be undone.</span>
          </div>
          <div className="btn-row">
            <button type="button" className="btn" disabled={busy || !reason.trim()} onClick={reverse}>{busy ? 'Reversing' : 'Reverse payment'}</button>
            <button type="button" className="btn btn-quiet" onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </div>
      ) : null}
    </li>
  );
}

export default function Payments() {
  const [list, reload] = useLoad('/api/helpdesk/payments?limit=100');
  return (
    <>
      <h1>Payments</h1>
      <p className="muted" style={{ margin: '6px 0 20px' }}>Recent payments, newest first. Reverse one if it was recorded by mistake.</p>
      {list.status === 'loading' ? <Loading>Loading payments</Loading> : null}
      {list.status === 'error' ? <Notice kind="error">{list.message}<button type="button" className="btn btn-link" onClick={reload}>Try again</button></Notice> : null}
      {list.status === 'ready' && list.data.length === 0 ? <p className="muted">No payments yet.</p> : null}
      {list.status === 'ready' && list.data.length > 0 ? (
        <ul className="txns">{list.data.map((p) => <PaymentRow key={p.paymentId} p={p} onChanged={reload} />)}</ul>
      ) : null}
    </>
  );
}
