import { useState } from 'react';
import { Loading, Notice } from '../../components/ui.jsx';
import { formatDate, formatMoney, formatPeriod, humanize, plural } from '../../format.js';
import { useLoad } from '../../useLoad.js';

function thisMonth() {
  return new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', timeZone: 'Asia/Kolkata' }).format(new Date());
}

// A horizontal bar whose length is the share of `max`. The number is always printed next to it.
function BarList({ items, max, format = (v) => String(v), empty }) {
  const top = max ?? Math.max(1, ...items.map((i) => Number(i.value)));
  if (items.every((i) => Number(i.value) === 0)) return <p className="muted">{empty}</p>;
  return (
    <ul className="bars">
      {items.map((i) => (
        <li key={i.label} className="bar-row">
          <span className="bar-label">{i.label}</span>
          <span className="bar-track" aria-hidden="true"><span className={`bar-fill ${i.tone ?? ''}`} style={{ width: `${(Number(i.value) / top) * 100}%` }} /></span>
          <span className="bar-value num">{format(i.value)}</span>
        </li>
      ))}
    </ul>
  );
}

function Figure({ label, value, note }) {
  return (
    <div className="figure">
      <dt>{label}</dt>
      <dd className="num">{value}</dd>
      {note ? <span className="hint">{note}</span> : null}
    </div>
  );
}

function Money({ m, period }) {
  const rate = Math.min(100, Math.max(0, Number(m.periodCollectionRatePercent)));
  const month = formatPeriod(`${period}-01`);
  return (
    <section className="panel" aria-labelledby="h-money">
      <h2 id="h-money">Collection for {month}</h2>
      <p className="lead num">{rate.toFixed(1)}% <span>of {formatMoney(m.periodBilled)} billed has been paid</span></p>
      <div className="meter" role="img" aria-label={`${rate.toFixed(1)} percent collected`}><span style={{ width: `${rate}%` }} /></div>
      <dl className="figures">
        <Figure label="Still to collect for this month" value={formatMoney(m.periodOutstanding)} />
        <Figure label="Received during this month" value={formatMoney(m.collectedInPeriod)} note="Includes payments for older bills" />
        <Figure label="Total dues, all months" value={formatMoney(m.totalDues)} />
        <Figure label="Advance credit held" value={formatMoney(m.advanceCredit)} />
      </dl>
    </section>
  );
}

function Overdue({ o, defaulters }) {
  return (
    <section className="panel" aria-labelledby="h-overdue">
      <h2 id="h-overdue">Overdue</h2>
      <p className="lead num">{formatMoney(o.amount)} <span>overdue across {plural(Number(o.flats), 'flat', 'flats')}</span></p>
      <BarList
        empty="Nothing is overdue."
        format={formatMoney}
        items={[
          { label: '1 to 30 days', value: o.days1to30 },
          { label: '31 to 60 days', value: o.days31to60 },
          { label: 'Over 60 days', value: o.days61plus, tone: 'bar-warn' },
        ]}
      />
      <h3>Flats owing the most</h3>
      {defaulters.length === 0 ? <p className="muted">No flat is overdue.</p> : (
        <table className="table">
          <thead><tr><th scope="col">Flat</th><th scope="col" className="r">Overdue</th><th scope="col" className="r">Oldest bill due</th><th scope="col" className="r">Days late</th></tr></thead>
          <tbody>
            {defaulters.map((d) => (
              <tr key={d.flatNo}>
                <th scope="row">{d.flatNo}</th>
                <td className="r num">{formatMoney(d.overdueAmount)}</td>
                <td className="r num">{formatDate(d.oldestDueDate)}</td>
                <td className="r num">{d.daysOverdue}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Reconciliation({ r }) {
  const c = r.countByStatus;
  return (
    <section className="panel" aria-labelledby="h-rec">
      <h2 id="h-rec">Bank statement matching</h2>
      {Number(r.pendingReview) > 0 || Number(r.unattributedAmount) > 0 ? (
        <p className="lead num">{formatMoney(r.unattributedAmount)} <span>in the bank but not yet linked to a flat</span></p>
      ) : <p className="lead"><span>Every bank credit is linked to a flat.</span></p>}
      <BarList
        empty="No bank statements uploaded yet."
        items={[
          { label: 'Matched automatically', value: c.AUTO_MATCHED },
          { label: 'Confirmed by help desk', value: c.CONFIRMED },
          { label: 'Waiting for review', value: c.NEEDS_REVIEW, tone: 'bar-warn' },
          { label: 'No match found', value: c.UNMATCHED, tone: 'bar-warn' },
          { label: 'Rejected', value: c.REJECTED },
        ]}
      />
    </section>
  );
}

function Complaints({ c }) {
  const byCategory = Object.entries(c.openByCategory).filter(([, v]) => v > 0).map(([k, v]) => ({ label: humanize(k), value: v })).sort((a, b) => b.value - a.value);
  return (
    <section className="panel" aria-labelledby="h-comp">
      <h2 id="h-comp">Complaints</h2>
      <p className="lead num">{c.byStatus.OPEN + c.byStatus.IN_PROGRESS} <span>open now, {c.openHighPriority} of them high priority</span></p>
      <dl className="figures">
        <Figure label="Open" value={c.byStatus.OPEN} />
        <Figure label="In progress" value={c.byStatus.IN_PROGRESS} />
        <Figure label="Resolved" value={c.byStatus.RESOLVED} />
        <Figure label="Average time to resolve" value={Number(c.avgResolutionHoursLast30Days) > 0 ? `${Number(c.avgResolutionHoursLast30Days)} hours` : 'None yet'} note="Last 30 days" />
      </dl>
      <h3>Open by priority</h3>
      <BarList empty="No open complaints." items={['HIGH', 'MEDIUM', 'LOW'].map((p) => ({ label: humanize(p), value: c.openByPriority[p] ?? 0, tone: p === 'HIGH' ? 'bar-warn' : '' }))} />
      <h3>Open by category</h3>
      <BarList empty="No open complaints." items={byCategory} />
      {Number(c.openLinkedDuplicates) > 0 ? <p className="row-sub">{plural(Number(c.openLinkedDuplicates), 'open complaint is', 'open complaints are')} linked to an earlier report of the same problem.</p> : null}
    </section>
  );
}

export default function Dashboard() {
  const [period, setPeriod] = useState(thisMonth());
  const valid = /^\d{4}-\d{2}$/.test(period);
  const [state, reload] = useLoad(`/api/committee/dashboard?period=${valid ? period : thisMonth()}`);

  return (
    <>
      <div className="dash-head">
        <div>
          <h1>Township overview</h1>
          {state.status === 'ready' ? <p className="muted">As of {formatDate(state.data.asOf)}</p> : null}
        </div>
        <div className="field dash-period">
          <label htmlFor="period">Month</label>
          <input id="period" type="month" value={period} onChange={(e) => setPeriod(e.target.value)} />
        </div>
      </div>

      {state.status === 'loading' ? <Loading>Loading the overview</Loading> : null}
      {state.status === 'error' ? <Notice kind="error">{state.message}<button type="button" className="btn btn-link" onClick={reload}>Try again</button></Notice> : null}
      {state.status === 'ready' ? (
        <div className="panels">
          <Money m={state.data.money} period={state.data.period} />
          <Overdue o={state.data.overdue} defaulters={state.data.topDefaulters} />
          <Reconciliation r={state.data.reconciliation} />
          <Complaints c={state.data.complaints} />
        </div>
      ) : null}
    </>
  );
}
