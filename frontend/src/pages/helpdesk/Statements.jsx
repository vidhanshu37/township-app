import { useRef, useState } from 'react';
import { api } from '../../api.js';
import { Notice } from '../../components/ui.jsx';
import { plural } from '../../format.js';
import ReviewQueue from './ReviewQueue.jsx';

const MAX_BYTES = 2 * 1024 * 1024;

function Summary({ s }) {
  const stats = [
    ['Credits read', s.credits],
    ['Matched automatically', s.autoMatched],
    ['Need your review', s.needsReview],
    ['No match found', s.unmatched],
    ['Already uploaded', s.duplicates],
    ['Debits skipped', s.debitsIgnored],
  ];
  return (
    <div className="summary" role="status">
      <h3>{plural(s.totalRows, 'row', 'rows')} read from the file</h3>
      <dl className="stats">
        {stats.map(([label, value]) => (
          <div key={label} className="stat"><dd className="num">{value}</dd><dt>{label}</dt></div>
        ))}
      </dl>
      {s.errors?.length ? (
        <details className="row-errors">
          <summary>{plural(s.errors.length, 'row', 'rows')} could not be read</summary>
          <ul>{s.errors.map((e) => <li key={e.lineNo}>Line {e.lineNo}: {e.message}</li>)}</ul>
        </details>
      ) : null}
    </div>
  );
}

export default function Statements() {
  const input = useRef(null);
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState(null);
  const [refresh, setRefresh] = useState(0);

  function pick(e) {
    const f = e.target.files?.[0] ?? null;
    setSummary(null);
    setError(f && f.size > MAX_BYTES ? 'That file is over 2 MB. Split the statement and upload it in parts.' : '');
    setFile(f && f.size <= MAX_BYTES ? f : null);
  }

  async function upload(e) {
    e.preventDefault();
    if (!file || busy) return;
    setBusy(true);
    setError('');
    setSummary(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      setSummary(await api('/api/helpdesk/statements', { method: 'POST', formData }));
      setFile(null);
      if (input.current) input.current.value = '';
      setRefresh((n) => n + 1);
    } catch (err) {
      setError(err.status === 400 ? `${err.message} The CSV needs date, narration and credit columns.` : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1>Bank statements</h1>
      <form className="sheet" onSubmit={upload}>
        <h2>Upload a statement</h2>
        {error ? <Notice kind="error">{error}</Notice> : null}
        <div className="field">
          <label htmlFor="statement">CSV file</label>
          <input id="statement" ref={input} type="file" accept=".csv,text/csv" onChange={pick} />
          <span className="hint">Columns: date, narration, credit. Optional: debit, utr. Up to 2 MB. Uploading the same file again changes nothing.</span>
        </div>
        <button type="submit" className="btn" disabled={!file || busy}>{busy ? 'Matching payments' : 'Upload and match'}</button>
      </form>
      {summary ? <Summary s={summary} /> : null}
      <ReviewQueue refreshKey={refresh} />
    </>
  );
}
