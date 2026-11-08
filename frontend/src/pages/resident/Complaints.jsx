import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDateTime, humanize } from '../../format.js';

const MIN = 10;
const MAX = 1000;

export function StatusChip({ status }) {
  const cls = status === 'RESOLVED' ? 'chip-paid' : status === 'IN_PROGRESS' ? 'chip-due' : 'chip-open';
  return <span className={`chip ${cls}`}>{humanize(status)}</span>;
}

export function PriorityChip({ priority }) {
  if (priority !== 'HIGH') return <span className="hint">{humanize(priority)} priority</span>;
  return <span className="chip chip-overdue">High priority</span>;
}

function ComplaintForm({ onCreated }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const length = text.trim().length;
  const valid = length >= MIN && length <= MAX;

  async function submit(e) {
    e.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    setError('');
    try {
      const created = await api('/api/resident/complaints', { method: 'POST', body: { text: text.trim() } });
      setText('');
      onCreated(created);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="sheet" onSubmit={submit} noValidate>
      <h2>Report a problem</h2>
      {error ? <Notice kind="error">{error}</Notice> : null}
      <div className="field">
        <label htmlFor="complaint">What is wrong?</label>
        <textarea id="complaint" rows={4} value={text} maxLength={MAX} onChange={(e) => setText(e.target.value)} aria-describedby="complaint-hint" />
        <span id="complaint-hint" className="hint">
          Say where it is and what happened. {length < MIN ? `At least ${MIN} characters.` : `${MAX - length} characters left.`}
        </span>
      </div>
      <button type="submit" className="btn btn-block" disabled={!valid || busy}>{busy ? 'Sending' : 'Send complaint'}</button>
    </form>
  );
}

export function ComplaintRow({ c }) {
  return (
    <li className="row complaint">
      <div>
        <div className="row-title">#{c.id} · {humanize(c.category)}</div>
        <p className="complaint-text">{c.text}</p>
        <div className="row-sub">{formatDateTime(c.createdAt)}</div>
        {c.duplicateOf ? <div className="row-sub">Linked to complaint #{c.duplicateOf}, which is already being handled.</div> : null}
      </div>
      <div className="row-side">
        <StatusChip status={c.status} />
        <PriorityChip priority={c.priority} />
      </div>
    </li>
  );
}

export default function Complaints() {
  const [state, setState] = useState({ status: 'loading' });
  const [created, setCreated] = useState(null);

  const load = useCallback(async () => {
    try {
      setState({ status: 'ready', rows: await api('/api/resident/complaints') });
    } catch (err) {
      setState({ status: 'error', message: err.message });
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <h1>Complaints</h1>
      <div style={{ margin: '20px 0' }}>
        {created ? (
          <Notice kind="ok">
            <strong>Complaint #{created.id} sent.</strong> Filed as {humanize(created.category).toLowerCase()}, {humanize(created.priority).toLowerCase()} priority.
            <button type="button" className="btn btn-link" onClick={() => setCreated(null)}>Report another problem</button>
          </Notice>
        ) : (
          <ComplaintForm onCreated={(c) => { setCreated(c); load(); }} />
        )}
      </div>

      <h2 className="section-title">Your complaints</h2>
      {state.status === 'loading' ? <Loading>Loading your complaints</Loading> : null}
      {state.status === 'error' ? (
        <Notice kind="error">{state.message}<button type="button" className="btn btn-link" onClick={load}>Try again</button></Notice>
      ) : null}
      {state.status === 'ready' && state.rows.length === 0 ? <p className="muted">You haven't reported anything yet.</p> : null}
      {state.status === 'ready' && state.rows.length > 0 ? <ul className="rows">{state.rows.map((c) => <ComplaintRow key={c.id} c={c} />)}</ul> : null}
    </>
  );
}
