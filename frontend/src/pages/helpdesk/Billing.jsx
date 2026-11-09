import { useState } from 'react';
import { api } from '../../api.js';
import { Notice } from '../../components/ui.jsx';
import { formatPeriod, plural } from '../../format.js';

// Current month in India time, as yyyy-MM
function thisMonth() {
  return new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', timeZone: 'Asia/Kolkata' }).format(new Date());
}


export default function Billing() {
  const [period, setPeriod] = useState(thisMonth());
  const [billsBusy, setBillsBusy] = useState(false);
  const [bills, setBills] = useState(null);
  const [feesBusy, setFeesBusy] = useState(false);
  const [fees, setFees] = useState(null);

  async function generate(e) {
    e.preventDefault();
    if (!/^\d{4}-\d{2}$/.test(period) || billsBusy) return;
    setBillsBusy(true);
    setBills(null);
    try {
      const r = await api('/api/helpdesk/bills/generate', { method: 'POST', body: { period } });
      const month = formatPeriod(`${period}-01`);
      setBills({
        kind: 'ok',
        text: r.billsCreated === 0
          ? `No new bills for ${month}. They were already generated.`
          : `${plural(r.billsCreated, 'bill', 'bills')} created for ${month}.`,
      });
    } catch (err) {
      setBills({ kind: 'error', text: err.message });
    } finally {
      setBillsBusy(false);
    }
  }

  async function applyFees() {
    setFeesBusy(true);
    setFees(null);
    try {
      const r = await api('/api/helpdesk/late-fees/apply', { method: 'POST' });
      setFees({
        kind: 'ok',
        text: r.lateFeesPosted === 0 ? 'No late fees to post. Everything is up to date.' : `${plural(r.lateFeesPosted, 'late fee', 'late fees')} posted.`,
      });
    } catch (err) {
      setFees({ kind: 'error', text: err.message });
    } finally {
      setFeesBusy(false);
    }
  }

  return (
    <>
      <h1>Billing</h1>
      <div className="grid-2">
        <form className="sheet" onSubmit={generate}>
          <h2>Generate monthly bills</h2>
          <div className="field">
            <label htmlFor="period">Month</label>
            <input id="period" type="month" value={period} onChange={(e) => setPeriod(e.target.value)} required />
            <span className="hint">One bill per flat. Running it twice for the same month does not create duplicates.</span>
          </div>
          {bills ? <Notice kind={bills.kind}>{bills.text}</Notice> : null}
          <button type="submit" className="btn btn-block" disabled={billsBusy || !period}>{billsBusy ? 'Generating' : 'Generate bills'}</button>
        </form>

        <div className="sheet">
          <h2>Apply late fees</h2>
          <p className="hint" style={{ marginBottom: 18 }}>
            Adds late fees to every overdue bill, up to today. Safe to run again: each day is charged once.
          </p>
          {fees ? <Notice kind={fees.kind}>{fees.text}</Notice> : null}
          <button type="button" className="btn btn-block" onClick={applyFees} disabled={feesBusy}>{feesBusy ? 'Applying' : 'Apply late fees'}</button>
        </div>
      </div>
    </>
  );
}
