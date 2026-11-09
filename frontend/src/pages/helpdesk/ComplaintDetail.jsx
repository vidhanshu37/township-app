import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDateTime, humanize } from '../../format.js';
import { useLoad } from '../../useLoad.js';
import { PriorityChip, StatusChip } from '../resident/Complaints.jsx';

// Same rules as the backend: what a complaint may move to from each status.
const NEXT = { OPEN: ['IN_PROGRESS', 'RESOLVED'], IN_PROGRESS: ['RESOLVED', 'OPEN'], RESOLVED: ['OPEN'] };
const ACTION = { IN_PROGRESS: 'Start work', RESOLVED: 'Mark resolved', OPEN: 'Reopen' };

function eventText(e) {
  if (e.eventType === 'STATUS_CHANGED' || (e.fromStatus && e.toStatus)) {
    return `Status changed from ${humanize(e.fromStatus).toLowerCase()} to ${humanize(e.toStatus).toLowerCase()}`;
  }
  return humanize(e.eventType);
}

export default function ComplaintDetail() {
  const { id } = useParams();
  const [state, reload] = useLoad(`/api/helpdesk/complaints/${encodeURIComponent(id)}`);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function move(status) {
    setBusy(true);
    setError('');
    try {
      await api(`/api/helpdesk/complaints/${encodeURIComponent(id)}/status`, { method: 'POST', body: { status, note: note.trim() || null } });
      setNote('');
      await reload();
    } catch (err) {
      setError(err.message);
      if (err.status === 409) reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <p><Link to="/helpdesk/complaints">Back to complaints</Link></p>
      {state.status === 'loading' ? <Loading>Loading complaint</Loading> : null}
      {state.status === 'error' ? <Notice kind="error">{state.message}</Notice> : null}
      {state.status === 'ready' ? (() => {
        const { complaint: c, events } = state.data;
        return (
          <>
            <h1 style={{ marginTop: 12 }}>#{c.id} · {humanize(c.category)}</h1>
            <div className="detail-meta">
              <StatusChip status={c.status} />
              <PriorityChip priority={c.priority} />
              <span className="hint">Flat {c.flatNo} · {formatDateTime(c.createdAt)}</span>
            </div>
            <p className="complaint-text detail-text">{c.text}</p>
            <p className="row-sub">Sorted by {c.triageSource === 'LLM' ? 'AI' : 'rules'}: {c.triageReason}</p>
            {c.duplicateOf ? <p className="row-sub">Possible duplicate of <Link to={`/helpdesk/complaints/${c.duplicateOf}`}>complaint #{c.duplicateOf}</Link>. Nothing was closed automatically.</p> : null}

            <div className="sheet" style={{ marginTop: 24 }}>
              <h2>Update status</h2>
              {error ? <Notice kind="error">{error}</Notice> : null}
              <div className="field">
                <label htmlFor="status-note">Note (optional)</label>
                <input id="status-note" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
              </div>
              <div className="btn-row">
                {NEXT[c.status].map((s, i) => (
                  <button key={s} type="button" className={i === 0 ? 'btn' : 'btn btn-quiet'} disabled={busy} onClick={() => move(s)}>{ACTION[s]}</button>
                ))}
              </div>
            </div>

            <h2 className="section-title">History</h2>
            {events.length === 0 ? <p className="muted">No history yet.</p> : (
              <ul className="rows">
                {events.map((e, i) => (
                  <li key={i} className="row">
                    <div>
                      <div className="row-title">{eventText(e)}</div>
                      {e.note ? <div className="row-sub">{e.note}</div> : null}
                    </div>
                    <div className="row-sub">{formatDateTime(e.createdAt)}</div>
                  </li>
                ))}
              </ul>
            )}
          </>
        );
      })() : null}
    </>
  );
}
