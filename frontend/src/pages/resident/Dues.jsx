import { useCallback, useEffect, useState } from 'react';
import Pay from './Pay.jsx';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import Nameplate from '../../components/Nameplate.jsx';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDate, formatMoney, formatPeriod, plural } from '../../format.js';

function StatusChip({ bill }) {
  if (bill.status === 'PAID') return <span className="chip chip-paid">Paid</span>;
  if (bill.status === 'OVERDUE') {
    return <span className="chip chip-overdue">Overdue {plural(bill.daysOverdue, 'day', 'days')}</span>;
  }
  return <span className="chip chip-due">Due</span>;
}

function BillRow({ bill }) {
  const paid = bill.status === 'PAID';
  return (
    <li className="row">
      <div>
        <div className="row-title">{formatPeriod(bill.period)}</div>
        <div className="row-sub">Due {formatDate(bill.dueDate)}</div>
      </div>
      <div className="row-side">
        <div className="row-amount num">{formatMoney(paid ? bill.amount : bill.outstanding)}</div>
        <StatusChip bill={bill} />
        {paid || Number(bill.outstanding) === Number(bill.amount) ? null : (
          <div className="row-sub num">Billed {formatMoney(bill.amount)}</div>
        )}
      </div>
    </li>
  );
}

export default function Dues() {
  const { user } = useAuth();
  const [state, setState] = useState({ status: 'loading' });

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setState({ status: 'loading' });
    try {
      setState({ status: 'ready', data: await api('/api/resident/dues') });
    } catch (err) {
      setState({ status: 'error', message: err.message });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <div className="hero">
        <Nameplate flatNo={user.flatNo} name={user.name} size="lg" />
      </div>

      {state.status === 'loading' ? <Loading>Loading your dues</Loading> : null}

      {state.status === 'error' ? (
        <Notice kind="error">
          {state.message}
          <button type="button" className="btn btn-link" onClick={() => load()}>Try again</button>
        </Notice>
      ) : null}

      {state.status === 'ready' ? <DuesBody data={state.data} onPaid={() => load(true)} /> : null}
    </>
  );
}

function DuesBody({ data, onPaid }) {
  const balance = Number(data.balance);
  const label = balance > 0 ? 'You owe' : balance < 0 ? 'Advance credit' : 'Nothing due';
  const overdue = data.bills.filter((b) => b.status === 'OVERDUE').length;

  return (
    <>
      <p className="balance-label">{label}</p>
      <p className="balance num">{formatMoney(balance)}</p>
      {overdue > 0 ? (
        <p className="balance-note">
          {plural(overdue, 'bill is', 'bills are')} overdue. Late fees are added to overdue amounts.
        </p>
      ) : null}

      {balance > 0 ? <div className="pay-wrap"><Pay balance={balance} onPaid={onPaid} /></div> : null}

      <h2 className="section-title">Bills</h2>
      {data.bills.length === 0 ? (
        <p className="muted">No bills yet. They appear here once the office generates them.</p>
      ) : (
        <ul className="rows">
          {data.bills.map((bill) => <BillRow key={bill.id} bill={bill} />)}
        </ul>
      )}
    </>
  );
}
