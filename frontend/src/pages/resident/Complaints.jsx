import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDateTime, humanize } from '../../format.js';
import { MAX_PHOTOS, PhotoStrip, uploadPhotos } from '../../components/Photos.jsx';

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
  const [files, setFiles] = useState([]);
  const length = text.trim().length;
  const valid = length >= MIN && length <= MAX;

  async function submit(e) {
    e.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    setError('');
    try {
      const created = await api('/api/resident/complaints', { method: 'POST', body: { text: text.trim() } });
      const failed = files.length ? await uploadPhotos(created.id, files) : 0;
      setText('');
      setFiles([]);
      onCreated(created, failed);
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
        <div className="field">
          <label htmlFor="photos">Photos (optional, up to {MAX_PHOTOS})</label>
          <input id="photos" type="file" accept="image/*" multiple onChange={(e) => setFiles(Array.from(e.target.files || []).slice(0, MAX_PHOTOS))} />
          {files.length ? <span className="hint">{files.length} photo{files.length > 1 ? 's' : ''} selected.</span> : null}
        </div>
        <button type="submit" className="btn btn-block" disabled={!valid || busy}>{busy ? 'Sending' : 'Send complaint'}</button>
      </form>
  );
}

export function ReplyStatus({ c }) {
  if (c.firstResponseAt) return <div className="row-sub">First reply {formatDateTime(c.firstResponseAt)}</div>;
  if (c.status === 'RESOLVED') return null;
  if (c.responseBreached) return <div className="sla-late">Reply was due {formatDateTime(c.responseDueAt)}. Overdue.</div>;
  return <div className="row-sub">Reply due by {formatDateTime(c.responseDueAt)}</div>;
}

function RatingBox({ c, onRated }) {
  const [value, setValue] = useState(0);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (c.rating) return <div className="row-sub">You rated this {c.rating} out of 5.</div>;

  async function send() {
    if (!value || busy) return;
    setBusy(true);
    setError('');
    try {
      await api(`/api/resident/complaints/${c.id}/rating`, { method: 'POST', body: { rating: value, comment: comment.trim() || null } });
      onRated();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
      <div className="rating">
        <div className="row-sub" id={`rate-${c.id}`}>How was the fix?</div>
        <div className="stars" role="group" aria-labelledby={`rate-${c.id}`}>
          {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} type="button" className={n <= value ? 'star on' : 'star'} aria-pressed={n === value} aria-label={`${n} out of 5`} onClick={() => setValue(n)}>{n}</button>
          ))}
        </div>
        <input className="rating-comment" aria-label="Comment (optional)" placeholder="Comment (optional)" maxLength={200} value={comment} onChange={(e) => setComment(e.target.value)} />
        {error ? <div className="sla-late">{error}</div> : null}
        <button type="button" className="btn btn-quiet" disabled={!value || busy} onClick={send}>{busy ? 'Sending' : 'Send rating'}</button>
      </div>
  );
}

export function ComplaintRow({ c, to, onRated }) {
  return (
      <li className="row complaint">
        <div>
          <div className="row-title">
            {to ? <Link to={to}>#{c.id} · {humanize(c.category)}</Link> : <>#{c.id} · {humanize(c.category)}</>}
            {to ? <span className="row-sub"> Flat {c.flatNo}</span> : null}
          </div>
          <p className="complaint-text">{c.text}</p>
          <div className="row-sub">{formatDateTime(c.createdAt)}{c.assignedToName ? ` · With ${c.assignedToName}` : ''}</div>
          <ReplyStatus c={c} />
          {!to ? <PhotoStrip base={`/api/resident/complaints/${c.id}`} /> : null}
          {!to && c.status === 'RESOLVED' && onRated ? <RatingBox c={c} onRated={onRated} /> : null}
          {c.duplicateOf ? <div className="row-sub">Linked to complaint #{c.duplicateOf}, which is already being handled.</div> : null}
        </div>
        <div className="row-side">
          <StatusChip status={c.status} />
          <PriorityChip priority={c.priority} />
          {c.escalated ? <span className="chip chip-overdue">Sent to duty manager</span> : null}
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
                {created.failedPhotos ? <> {created.failedPhotos} photo{created.failedPhotos > 1 ? 's' : ''} could not be uploaded.</> : null}
                <button type="button" className="btn btn-link" onClick={() => setCreated(null)}>Report another problem</button>
              </Notice>
          ) : (
              <ComplaintForm onCreated={(c, failed) => { setCreated({ ...c, failedPhotos: failed }); load(); }} />
          )}
        </div>

        <h2 className="section-title">Your complaints</h2>
        {state.status === 'loading' ? <Loading>Loading your complaints</Loading> : null}
        {state.status === 'error' ? (
            <Notice kind="error">{state.message}<button type="button" className="btn btn-link" onClick={load}>Try again</button></Notice>
        ) : null}
        {state.status === 'ready' && state.rows.length === 0 ? <p className="muted">You haven't reported anything yet.</p> : null}
        {state.status === 'ready' && state.rows.length > 0 ? <ul className="rows">{state.rows.map((c) => <ComplaintRow key={c.id} c={c} onRated={load} />)}</ul> : null}
      </>
  );
}