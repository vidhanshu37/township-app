import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDateTime, formatMoney, humanize } from '../../format.js';

// Ledger amounts are signed: charges are positive, payments and credits negative.
export default function Ledger() {
  const [state, setState] = useState({ status: 'loading' });

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', rows: await api('/api/resident/ledger') });
    } catch (err) {
      setState({ status: 'error', message: err.message });
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <h1>Ledger</h1>
      <p className="muted" style={{ margin: '6px 0 20px' }}>Every charge and payment on your flat, newest first.</p>
      {state.status === 'loading' ? <Loading>Loading your ledger</Loading> : null}
      {state.status === 'error' ? (
        <Notice kind="error">{state.message}<button type="button" className="btn btn-link" onClick={load}>Try again</button></Notice>
      ) : null}
      {state.status === 'ready' && state.rows.length === 0 ? <p className="muted">Nothing here yet. Charges and payments appear as they happen.</p> : null}
      {state.status === 'ready' && state.rows.length > 0 ? (
        <ul className="rows">
          {state.rows.map((r) => {
            const credit = Number(r.amount) < 0;
            const receiptId = credit && typeof r.reference === 'string' && /^pay_|^bank_/.test(r.reference) ? r.reference : null;
            return (
              <li key={r.id} className="row">
                <div>
                  <div className="row-title">{humanize(r.entryType)}</div>
                  <div className="row-sub">{formatDateTime(r.createdAt)}</div>
                  {r.reference ? <div className="row-sub num">{r.reference}</div> : null}
                  {receiptId ? <Link to={`/resident/receipt/${encodeURIComponent(receiptId)}`}>View receipt</Link> : null}
                </div>
                <div className={`row-amount num ${credit ? 'amt-credit' : ''}`}>
                  {credit ? '− ' : '+ '}{formatMoney(r.amount)}
                </div>
              </li>
            );
          })}
        </ul>
      ) : null}
    </>
  );
}
