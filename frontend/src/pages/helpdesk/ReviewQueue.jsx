import { useState } from 'react';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDate, formatMoney, humanize } from '../../format.js';
import { useLoad } from '../../useLoad.js';

const FILTERS = [
  ['NEEDS_REVIEW', 'Needs review'],
  ['UNMATCHED', 'No match'],
  ['AUTO_MATCHED', 'Auto matched'],
  ['CONFIRMED', 'Confirmed'],
  ['REJECTED', 'Rejected'],
];

function StatusChip({ status }) {
  const cls = status === 'CONFIRMED' || status === 'AUTO_MATCHED' ? 'chip-paid'
    : status === 'REJECTED' ? 'chip-overdue' : 'chip-due';
  return <span className={`chip ${cls}`}>{humanize(status)}</span>;
}

function Txn({ t, flats, onChanged }) {
  const actionable = t.status === 'NEEDS_REVIEW' || t.status === 'UNMATCHED';
  const [flatId, setFlatId] = useState(t.flatId ? String(t.flatId) : '');
  const [note, setNote] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function act(path, body) {
    setBusy(true);
    setError('');
    try {
      await api(`/api/helpdesk/bank-txns/${t.id}/${path}`, { method: 'POST', body });
      onChanged();
    } catch (err) {
      setError(err.message);
      if (err.status === 409) onChanged(); // someone else already handled it: show the latest list
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="txn">
      <div className="txn-head">
        <div>
          <div className="row-title num">{formatMoney(t.amount)}</div>
          <div className="row-sub">{formatDate(t.date)} · line {t.lineNo}{t.utr ? ` · UTR ${t.utr}` : ''}</div>
        </div>
        <div className="row-side">
          <StatusChip status={t.status} />
          {t.flatNo ? <span className="hint">Flat {t.flatNo}</span> : null}
        </div>
      </div>
      <p className="narration">{t.narration}</p>
      {t.reasons ? <p className="row-sub">Why: {t.reasons} (score {t.score})</p> : null}
      {t.resolutionNote ? <p className="row-sub">Note: {t.resolutionNote}</p> : null}
      {t.paymentId ? <p className="row-sub num">Payment {t.paymentId}{t.paymentStatus === 'REVERSED' ? ' (reversed)' : ''}</p> : null}

      {actionable ? (
        <div className="txn-actions">
          {error ? <Notice kind="error">{error}</Notice> : null}
          {rejecting ? (
            <div className="field">
              <label htmlFor={`reason-${t.id}`}>Why reject this?</label>
              <input id={`reason-${t.id}`} value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
              <div className="btn-row">
                <button type="button" className="btn" disabled={busy || !reason.trim()} onClick={() => act('reject', { reason: reason.trim() })}>Reject payment</button>
                <button type="button" className="btn btn-quiet" onClick={() => setRejecting(false)}>Cancel</button>
              </div>
            </div>
          ) : (
            <>
              <div className="field">
                <label htmlFor={`flat-${t.id}`}>Credit this payment to</label>
                <select id={`flat-${t.id}`} value={flatId} onChange={(e) => setFlatId(e.target.value)}>
                  <option value="">Choose a flat</option>
                  {flats.map((f) => <option key={f.id} value={f.id}>{f.flatNo}{f.residentName ? `, ${f.residentName}` : ''}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor={`note-${t.id}`}>Note (optional)</label>
                <input id={`note-${t.id}`} value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
              </div>
              <div className="btn-row">
                <button type="button" className="btn" disabled={busy || !flatId} onClick={() => act('confirm', { flatId: Number(flatId), note: note.trim() || null })}>Confirm payment</button>
                <button type="button" className="btn btn-quiet" onClick={() => setRejecting(true)}>Reject</button>
              </div>
            </>
          )}
        </div>
      ) : null}
    </li>
  );
}

export default function ReviewQueue({ refreshKey }) {
  const [status, setStatus] = useState('NEEDS_REVIEW');
  const [list, reload] = useLoad(`/api/helpdesk/bank-txns?status=${status}&limit=100&r=${refreshKey}`);
  const [flats] = useLoad('/api/helpdesk/flats');

  return (
    <>
      <h2 className="section-title">Payments from the bank</h2>
      <div className="filters" role="group" aria-label="Show payments that are">
        {FILTERS.map(([value, label]) => (
          <button key={value} type="button" className={`filter ${status === value ? 'on' : ''}`} aria-pressed={status === value} onClick={() => setStatus(value)}>{label}</button>
        ))}
      </div>
      {list.status === 'loading' ? <Loading>Loading payments</Loading> : null}
      {list.status === 'error' ? <Notice kind="error">{list.message}<button type="button" className="btn btn-link" onClick={reload}>Try again</button></Notice> : null}
      {list.status === 'ready' && list.data.length === 0 ? (
        <p className="muted">{status === 'NEEDS_REVIEW' ? 'Nothing waiting for review.' : `No ${humanize(status).toLowerCase()} payments.`}</p>
      ) : null}
      {list.status === 'ready' && list.data.length > 0 ? (
        <ul className="txns">
          {list.data.map((t) => <Txn key={t.id} t={t} flats={flats.status === 'ready' ? flats.data : []} onChanged={reload} />)}
        </ul>
      ) : null}
    </>
  );
}
