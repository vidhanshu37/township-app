import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDateTime, formatMoney, humanize } from '../../format.js';

// Shows every field the receipt endpoint returns, so it stays correct if the backend adds a field.
function show(key, value) {
  if (value === null || value === undefined || value === '') return null;
  if (/amount|total|paid$/i.test(key) && !Number.isNaN(Number(value))) return formatMoney(value);
  if (/(at|date|time)$/i.test(key) && !Number.isNaN(Date.parse(value))) return formatDateTime(value);
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export default function Receipt() {
  const { paymentId } = useParams();
  const [state, setState] = useState({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;
    api(`/api/resident/payments/${encodeURIComponent(paymentId)}/receipt`)
      .then((data) => !cancelled && setState({ status: 'ready', data }))
      .catch((err) => !cancelled && setState({ status: 'error', message: err.message }));
    return () => { cancelled = true; };
  }, [paymentId]);

  return (
    <>
      <p><Link to="/resident/ledger">Back to ledger</Link></p>
      <h1 style={{ marginTop: 12 }}>Receipt</h1>
      {state.status === 'loading' ? <Loading>Loading receipt</Loading> : null}
      {state.status === 'error' ? <Notice kind="error">{state.message}</Notice> : null}
      {state.status === 'ready' ? (
        <>
          <dl className="facts">
            {Object.entries(state.data).map(([key, value]) => {
              const text = Array.isArray(value) ? null : show(key, value);
              if (text === null && !Array.isArray(value)) return null;
              if (Array.isArray(value)) {
                return (
                  <div key={key} className="fact">
                    <dt>{humanize(key.replace(/([A-Z])/g, '_$1'))}</dt>
                    <dd>{value.map((v, i) => <div key={i} className="num">{typeof v === 'object' ? Object.values(v).map((x) => show('x', x)).filter(Boolean).join(' · ') : String(v)}</div>)}</dd>
                  </div>
                );
              }
              return (
                <div key={key} className="fact">
                  <dt>{humanize(key.replace(/([A-Z])/g, '_$1'))}</dt>
                  <dd className="num">{text}</dd>
                </div>
              );
            })}
          </dl>
          <button type="button" className="btn btn-quiet" onClick={() => window.print()}>Print receipt</button>
        </>
      ) : null}
    </>
  );
}
