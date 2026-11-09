import { useState } from 'react';
import { Loading, Notice } from '../../components/ui.jsx';
import { useLoad } from '../../useLoad.js';
import { ComplaintRow } from '../resident/Complaints.jsx';

const STATUS = [['', 'All'], ['OPEN', 'Open'], ['IN_PROGRESS', 'In progress'], ['RESOLVED', 'Resolved']];
const PRIORITY = [['', 'Any priority'], ['HIGH', 'High'], ['MEDIUM', 'Medium'], ['LOW', 'Low']];

export default function ComplaintQueue() {
  const [status, setStatus] = useState('OPEN');
  const [priority, setPriority] = useState('');
  const query = new URLSearchParams({ limit: '100' });
  if (status) query.set('status', status);
  if (priority) query.set('priority', priority);
  const [list, reload] = useLoad(`/api/helpdesk/complaints?${query}`);

  return (
    <>
      <h1>Complaints</h1>
      <div className="filters" role="group" aria-label="Show complaints that are">
        {STATUS.map(([value, label]) => (
          <button key={label} type="button" className={`filter ${status === value ? 'on' : ''}`} aria-pressed={status === value} onClick={() => setStatus(value)}>{label}</button>
        ))}
        <label className="visually-hidden" htmlFor="priority">Priority</label>
        <select id="priority" className="filter-select" value={priority} onChange={(e) => setPriority(e.target.value)}>
          {PRIORITY.map(([value, label]) => <option key={label} value={value}>{label}</option>)}
        </select>
      </div>
      {list.status === 'loading' ? <Loading>Loading complaints</Loading> : null}
      {list.status === 'error' ? <Notice kind="error">{list.message}<button type="button" className="btn btn-link" onClick={reload}>Try again</button></Notice> : null}
      {list.status === 'ready' && list.data.length === 0 ? <p className="muted">No complaints match these filters.</p> : null}
      {list.status === 'ready' && list.data.length > 0 ? (
        <ul className="rows">{list.data.map((c) => <ComplaintRow key={c.id} c={c} to={`/helpdesk/complaints/${c.id}`} />)}</ul>
      ) : null}
    </>
  );
}
