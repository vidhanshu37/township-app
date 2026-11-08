// A tiny stand-in for the Spring Boot API, for working on the screens without the backend running.
// It copies the real JSON shapes. Run it with "npm run mock", then start Vite with VITE_API_TARGET=http://localhost:8086
import http from 'node:http';

const PORT = Number(process.env.PORT || 8086);
const GAP_SECONDS = Number(process.env.MOCK_GAP_SECONDS ?? 30);
const OTP = '123456';

const USERS = {
  '9000000001': { id: 1, name: 'Neeraj Kulkarni', role: 'RESIDENT', flat_no: 'T07-1203', tower: 'T07' },
  '9000000002': { id: 2, name: 'Swati Joshi', role: 'RESIDENT', flat_no: 'T12-0408', tower: 'T12' },
  '9000000003': { id: 3, name: 'Aamir Shaikh', role: 'RESIDENT', flat_no: 'T21-1601', tower: 'T21' },
  '9000000004': { id: 4, name: 'Mock Advance', role: 'RESIDENT', flat_no: 'T03-0101', tower: 'T03' },
  '9000000005': { id: 5, name: 'Mock Empty', role: 'RESIDENT', flat_no: 'T04-0202', tower: 'T04' },
  '9000000006': { id: 6, name: 'Mock Broken', role: 'RESIDENT', flat_no: 'T05-0303', tower: 'T05' },
  '9000000010': { id: 10, name: 'Help Desk', role: 'HELP_DESK', flat_no: null, tower: null },
  '9000000020': { id: 20, name: 'Committee', role: 'COMMITTEE', flat_no: null, tower: null },
};

// outstanding is what the backend computes from the ledger (late fees included)
const BILLS = {
  '9000000001': [{ id: 1, period: '2026-11-01', amount: 7475.0, dueDate: '2026-11-10', outstanding: 0 }],
  '9000000002': [
    { id: 4, period: '2026-11-01', amount: 6370.0, dueDate: '2026-11-10', outstanding: 6370.0 },
    { id: 2, period: '2026-09-01', amount: 6370.0, dueDate: '2026-09-10', outstanding: 6517.64 },
  ],
  '9000000003': [
    { id: 5, period: '2026-11-01', amount: 10660.0, dueDate: '2026-11-10', outstanding: 10660.0 },
    { id: 3, period: '2026-10-01', amount: 10660.0, dueDate: '2026-10-10', outstanding: 5710.24 },
  ],
  '9000000004': [{ id: 6, period: '2026-11-01', amount: 5000.0, dueDate: '2026-11-10', outstanding: 0 }],
  '9000000005': [],
};
const BALANCE = { '9000000001': 0, '9000000002': 12887.64, '9000000003': 16370.24, '9000000004': -630, '9000000005': 0 };

const lastRequest = new Map();
const ORDERS = new Map();
const PAYMENTS = new Map();
const LEDGER = {};
const COMPLAINTS = {};
let complaintSeq = 100;
const GENERATED = new Set();
let lateFeeRuns = 0;
const EVENTS = {};
const TXNS = [
  { id: 1, fileId: 1, lineNo: 2, date: '2026-11-03', narration: 'UPI/TWN0002/maintenance nov', amount: 6370.0, utr: 'UTR1001', status: 'AUTO_MATCHED', flatId: 2, flatNo: 'T12-0408', score: 90, reasons: 'reference, flat, amount', note: null, resolutionNote: null, paymentId: 'bank_1', paymentStatus: 'SUCCESS' },
  { id: 2, fileId: 1, lineNo: 3, date: '2026-11-04', narration: 'NEFT Kulkarni N maintenance', amount: 7475.0, utr: 'UTR1002', status: 'NEEDS_REVIEW', flatId: 1, flatNo: 'T07-1203', score: 55, reasons: 'amount, name', note: null, resolutionNote: null, paymentId: null, paymentStatus: null },
  { id: 3, fileId: 1, lineNo: 4, date: '2026-11-05', narration: 'IMPS from Shaikh A', amount: 5000.0, utr: null, status: 'NEEDS_REVIEW', flatId: null, flatNo: null, score: 35, reasons: 'name', note: null, resolutionNote: null, paymentId: null, paymentStatus: null },
  { id: 4, fileId: 1, lineNo: 5, date: '2026-11-05', narration: 'CASH DEPOSIT', amount: 1234.0, utr: null, status: 'UNMATCHED', flatId: null, flatNo: null, score: 0, reasons: '', note: null, resolutionNote: null, paymentId: null, paymentStatus: null },
];
const HD_PAYMENTS = [
  { paymentId: 'pay_aaaa1111', orderId: 'order_1', flatNo: 'T07-1203', amount: 7475.0, status: 'SUCCESS', source: 'GATEWAY', paidAt: '2026-11-06T10:15:00+05:30' },
  { paymentId: 'bank_1', orderId: null, flatNo: 'T12-0408', amount: 6370.0, status: 'SUCCESS', source: 'BANK', paidAt: '2026-11-03T00:00:00+05:30' },
  { paymentId: 'pay_bbbb2222', orderId: 'order_2', flatNo: 'T21-1601', amount: 500.0, status: 'REVERSED', source: 'GATEWAY', paidAt: '2026-11-01T09:00:00+05:30' },
];
const SEED = [
  { id: 1, flatId: 3, flatNo: 'T21-1601', text: 'Water leakage from ceiling in the bathroom, getting worse', category: 'PLUMBING', priority: 'MEDIUM', status: 'OPEN', triageSource: 'RULES', triageReason: 'matched keyword: leakage', duplicateOf: null, assignedTo: null, createdAt: '2026-11-07T09:00:00+05:30', updatedAt: '2026-11-07T09:00:00+05:30' },
  { id: 2, flatId: 2, flatNo: 'T12-0408', text: 'Corridor light not working near lift lobby', category: 'ELECTRICAL', priority: 'LOW', status: 'IN_PROGRESS', triageSource: 'RULES', triageReason: 'matched keyword: light', duplicateOf: null, assignedTo: 10, createdAt: '2026-11-06T18:00:00+05:30', updatedAt: '2026-11-07T10:00:00+05:30' },
];
function allComplaints() { return [...Object.values(COMPLAINTS).flat(), ...SEED].sort((a, b) => b.id - a.id); }

function send(res, status, body) {
  if (body === undefined) {
    res.writeHead(status);
    res.end();
    return;
  }
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        resolve({});
      }
    });
  });
}

function userFor(req) {
  const m = /^Bearer mock\.(\d{10})$/.exec(req.headers.authorization || '');
  return m && USERS[m[1]] ? { phone: m[1], ...USERS[m[1]] } : null;
}

function daysBetween(isoDue) {
  const [y, m, d] = isoDue.split('-').map(Number);
  return Math.floor((Date.now() - Date.UTC(y, m - 1, d)) / 86400000);
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const route = `${req.method} ${url.pathname}`;

  if (route === 'POST /api/auth/request-otp') {
    const { phone } = await readBody(req);
    if (!/^\d{10}$/.test(phone || '')) return send(res, 400, { status: 400, error: 'Bad Request', message: 'Validation failed' });
    const last = lastRequest.get(phone);
    if (last && Date.now() - last < GAP_SECONDS * 1000) {
      return send(res, 429, { status: 429, error: 'Too Many Requests', message: 'Please wait before asking for another code' });
    }
    lastRequest.set(phone, Date.now());
    return send(res, 200, {
      message: 'If the number is registered, an OTP has been sent',
      demoOtp: USERS[phone] ? OTP : null,
    });
  }

  if (route === 'POST /api/auth/verify-otp') {
    const { phone, otp } = await readBody(req);
    const u = USERS[phone];
    if (!u || otp !== OTP) return send(res, 401, { error: 'Invalid or expired OTP' });
    return send(res, 200, { accessToken: `mock.${phone}`, expiresInSeconds: 3600, role: u.role, name: u.name });
  }

  const me = userFor(req);
  if (!me) return send(res, 401); // the real backend sends an empty 401

  if (route === 'GET /api/me') {
    const { phone, ...rest } = me;
    return send(res, 200, { ...rest, resident_type: me.role === 'RESIDENT' ? 'OWNER' : null });
  }

  if (route === 'GET /api/resident/dues') {
    if (me.role !== 'RESIDENT') return send(res, 403);
    if (me.phone === '9000000006') return send(res, 500, { status: 500, error: 'Internal Server Error' });
    const bills = (BILLS[me.phone] || []).map((b) => {
      const overdue = b.outstanding > 0 ? Math.max(0, daysBetween(b.dueDate)) : 0;
      const status = b.outstanding <= 0 ? 'PAID' : overdue > 0 ? 'OVERDUE' : 'DUE';
      return { ...b, daysOverdue: overdue, status };
    });
    return send(res, 200, { balance: BALANCE[me.phone] ?? 0, bills });
  }


  // ---- payments, ledger, complaints (state lives in memory; restart the mock to reset) ----
  if (route === 'POST /api/resident/payments/orders' && me.role === 'RESIDENT') {
    const { amount } = await readBody(req);
    if (amount !== null && amount !== undefined && !(Number(amount) >= 1)) return send(res, 400, { status: 400, error: 'Bad Request', message: 'Validation failed' });
    const total = amount ?? BALANCE[me.phone] ?? 0;
    if (!(total > 0)) return send(res, 409, { status: 409, error: 'Conflict', message: 'Nothing to pay' });
    const orderId = `order_${Date.now()}`;
    ORDERS.set(orderId, { phone: me.phone, amount: Number(total) });
    return send(res, 200, { orderId, amount: Number(total) });
  }
  let m = /^POST \/api\/resident\/payments\/orders\/([^/]+)\/simulate$/.exec(route);
  if (m && me.role === 'RESIDENT') {
    const o = ORDERS.get(m[1]);
    if (!o || o.phone !== me.phone) return send(res, 404, { status: 404, error: 'Not Found' });
    const paymentId = `pay_${String(Date.now()).slice(-10)}`;
    PAYMENTS.set(paymentId, { ...o, paymentId, at: new Date().toISOString() });
    BALANCE[me.phone] = Math.round(((BALANCE[me.phone] ?? 0) - o.amount) * 100) / 100;
    if (BALANCE[me.phone] <= 0) (BILLS[me.phone] || []).forEach((b) => (b.outstanding = 0));
    (LEDGER[me.phone] ||= []).unshift({ id: Date.now(), entryType: 'PAYMENT', amount: -o.amount, reference: paymentId, createdAt: new Date().toISOString() });
    return send(res, 200, { status: 'PAID', message: 'Payment recorded', paymentId });
  }
  m = /^GET \/api\/resident\/payments\/([^/]+)\/receipt$/.exec(route);
  if (m && me.role === 'RESIDENT') {
    const p = PAYMENTS.get(m[1]);
    if (!p || p.phone !== me.phone) return send(res, 404, { status: 404, error: 'Not Found' });
    return send(res, 200, { paymentId: p.paymentId, flatNo: me.flat_no, amount: p.amount, paidAt: p.at, status: 'SUCCESS' });
  }
  if (route === 'GET /api/resident/ledger' && me.role === 'RESIDENT') {
    if (me.phone === '9000000005') return send(res, 200, []);
    const base = LEDGER[me.phone] || [];
    const bills = (BILLS[me.phone] || []).map((b) => ({ id: 1000 + b.id, entryType: 'BILL', amount: b.amount, reference: `bill ${b.period.slice(0, 7)}`, createdAt: `${b.period}T09:00:00+05:30` }));
    return send(res, 200, [...base, ...bills]);
  }
  if (route === 'POST /api/resident/complaints' && me.role === 'RESIDENT') {
    const { text } = await readBody(req);
    if (typeof text !== 'string' || text.trim().length < 10) return send(res, 400, { status: 400, error: 'Bad Request', message: 'Validation failed' });
    const high = /lift|fire|leak|smoke|short circuit/i.test(text);
    const list = (COMPLAINTS[me.phone] ||= []);
    const dup = list.find((c) => c.category === (high ? 'SAFETY' : 'OTHER') && c.status !== 'RESOLVED');
    const c = { id: ++complaintSeq, flatId: me.id, flatNo: me.flat_no, text: text.trim(), category: high ? 'SAFETY' : 'OTHER', priority: high ? 'HIGH' : 'LOW', status: 'OPEN', triageSource: 'RULES', triageReason: 'mock', duplicateOf: dup ? dup.id : null, assignedTo: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
    list.unshift(c);
    return send(res, 201, c);
  }
  if (route === 'GET /api/resident/complaints' && me.role === 'RESIDENT') return send(res, 200, COMPLAINTS[me.phone] || []);

  if (route === 'GET /api/committee/dashboard' && me.role === 'COMMITTEE') {
    const period = url.searchParams.get('period') || '2026-11';
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(period)) return send(res, 400, { status: 400, message: 'period must look like 2026-11' });
    if (period === '2026-01') return send(res, 500, { status: 500 });
    const empty = period === '2025-01';
    return send(res, 200, {
      period, asOf: '2026-11-08',
      money: empty ? { periodBilled: 0, periodOutstanding: 0, periodCollectionRatePercent: 0, collectedInPeriod: 0, totalDues: 0, advanceCredit: 0 }
        : { periodBilled: 42175.0, periodOutstanding: 28400.0, periodCollectionRatePercent: 32.7, collectedInPeriod: 21075.0, totalDues: 35258.0, advanceCredit: 630.0 },
      overdue: empty ? { amount: 0, flats: 0, days1to30: 0, days31to60: 0, days61plus: 0 } : { amount: 12887.64, flats: 2, days1to30: 5710.24, days31to60: 0, days61plus: 7177.4 },
      topDefaulters: empty ? [] : [{ flatNo: 'T12-0408', overdueAmount: 7177.4, oldestDueDate: '2026-09-10', daysOverdue: 59 }, { flatNo: 'T21-1601', overdueAmount: 5710.24, oldestDueDate: '2026-10-10', daysOverdue: 29 }],
      reconciliation: empty ? { countByStatus: { AUTO_MATCHED: 0, NEEDS_REVIEW: 0, UNMATCHED: 0, CONFIRMED: 0, REJECTED: 0 }, pendingReview: 0, unattributedAmount: 0 }
        : { countByStatus: { AUTO_MATCHED: 3, NEEDS_REVIEW: 2, UNMATCHED: 2, CONFIRMED: 1, REJECTED: 1 }, pendingReview: 2, unattributedAmount: 6234.0 },
      complaints: empty ? { byStatus: { OPEN: 0, IN_PROGRESS: 0, RESOLVED: 0 }, openByPriority: { HIGH: 0, MEDIUM: 0, LOW: 0 }, openByCategory: { PLUMBING: 0, OTHER: 0 }, openHighPriority: 0, openLinkedDuplicates: 0, avgResolutionHoursLast30Days: 0 }
        : { byStatus: { OPEN: 3, IN_PROGRESS: 1, RESOLVED: 4 }, openByPriority: { HIGH: 1, MEDIUM: 2, LOW: 1 }, openByCategory: { PLUMBING: 2, ELECTRICAL: 1, LIFT: 1, SECURITY: 0, SAFETY: 0, HOUSEKEEPING: 0, PARKING: 0, NOISE: 0, OTHER: 0 }, openHighPriority: 1, openLinkedDuplicates: 1, avgResolutionHoursLast30Days: 26.5 },
    });
  }

  // ---- help desk ----
  if (me.role === 'HELP_DESK') {
    if (route === 'GET /api/helpdesk/flats') return send(res, 200, [
      { id: 1, flatNo: 'T07-1203', tower: 'T07', residentName: 'Neeraj Kulkarni' },
      { id: 2, flatNo: 'T12-0408', tower: 'T12', residentName: 'Swati Joshi' },
      { id: 3, flatNo: 'T21-1601', tower: 'T21', residentName: 'Aamir Shaikh' },
      { id: 4, flatNo: 'T03-0101', tower: 'T03', residentName: null }]);
    if (route === 'POST /api/helpdesk/bills/generate') {
      const { period } = await readBody(req);
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(period || '')) return send(res, 400, { status: 400, message: 'Validation failed' });
      const n = GENERATED.has(period) ? 0 : 5; GENERATED.add(period);
      return send(res, 200, { period, billsCreated: n });
    }
    if (route === 'POST /api/helpdesk/late-fees/apply') { const n = lateFeeRuns++ === 0 ? 3 : 0; return send(res, 200, { lateFeesPosted: n }); }
    if (route === 'POST /api/helpdesk/statements') {
      await readBody(req);
      return send(res, 200, { fileId: 1, totalRows: 9, credits: 7, debitsIgnored: 1, duplicates: 0, autoMatched: 3, needsReview: 2, unmatched: 2, errors: [{ lineNo: 8, message: 'invalid date: 31/02/2026' }] });
    }
    if (route === 'GET /api/helpdesk/bank-txns') {
      const st = url.searchParams.get('status');
      if (!['AUTO_MATCHED', 'NEEDS_REVIEW', 'UNMATCHED', 'CONFIRMED', 'REJECTED'].includes(st)) return send(res, 400, { status: 400, message: 'Unknown status: ' + st });
      return send(res, 200, TXNS.filter((t) => t.status === st));
    }
    let tm = /^POST \/api\/helpdesk\/bank-txns\/(\d+)\/(confirm|reject)$/.exec(route);
    if (tm) {
      const t = TXNS.find((x) => x.id === Number(tm[1]));
      if (!t) return send(res, 404, { status: 404 });
      if (!['NEEDS_REVIEW', 'UNMATCHED'].includes(t.status)) return send(res, 409, { status: 409, message: 'This payment was already handled' });
      const b = await readBody(req);
      if (tm[2] === 'confirm') { t.status = 'CONFIRMED'; t.flatId = b.flatId; t.flatNo = { 1: 'T07-1203', 2: 'T12-0408', 3: 'T21-1601', 4: 'T03-0101' }[b.flatId]; t.resolutionNote = b.note; t.paymentId = 'bank_' + t.id; t.paymentStatus = 'SUCCESS'; }
      else { t.status = 'REJECTED'; t.resolutionNote = b.reason; }
      return send(res, 200, t);
    }
    if (route === 'GET /api/helpdesk/payments') return send(res, 200, HD_PAYMENTS);
    tm = /^POST \/api\/helpdesk\/payments\/([^/]+)\/reverse$/.exec(route);
    if (tm) {
      const p = HD_PAYMENTS.find((x) => x.paymentId === tm[1]);
      if (!p) return send(res, 404, { status: 404 });
      if (p.status === 'REVERSED') return send(res, 409, { status: 409, message: 'Payment is already reversed' });
      p.status = 'REVERSED'; return send(res, 200, { status: 'REVERSED' });
    }
    if (route === 'GET /api/helpdesk/complaints') {
      const st = url.searchParams.get('status'), pr = url.searchParams.get('priority');
      return send(res, 200, allComplaints().filter((c) => (!st || c.status === st) && (!pr || c.priority === pr)));
    }
    tm = /^GET \/api\/helpdesk\/complaints\/(\d+)$/.exec(route);
    if (tm) {
      const c = allComplaints().find((x) => x.id === Number(tm[1]));
      return c ? send(res, 200, { complaint: c, events: EVENTS[c.id] || [{ eventType: 'CREATED', fromStatus: null, toStatus: 'OPEN', note: null, actorId: null, createdAt: c.createdAt }] }) : send(res, 404, { status: 404 });
    }
    tm = /^POST \/api\/helpdesk\/complaints\/(\d+)\/status$/.exec(route);
    if (tm) {
      const c = allComplaints().find((x) => x.id === Number(tm[1]));
      if (!c) return send(res, 404, { status: 404 });
      const b = await readBody(req);
      const ok = { OPEN: ['IN_PROGRESS', 'RESOLVED'], IN_PROGRESS: ['OPEN', 'RESOLVED'], RESOLVED: ['OPEN'] }[c.status];
      if (!ok.includes(b.status)) return send(res, 409, { status: 409, message: 'Cannot move from ' + c.status + ' to ' + b.status });
      (EVENTS[c.id] ||= [{ eventType: 'CREATED', fromStatus: null, toStatus: 'OPEN', note: null, actorId: null, createdAt: c.createdAt }]).push({ eventType: 'STATUS_CHANGED', fromStatus: c.status, toStatus: b.status, note: b.note, actorId: 10, createdAt: new Date().toISOString() });
      c.status = b.status; return send(res, 200, c);
    }
  }

  return send(res, 404, { status: 404, error: 'Not Found' });
}).listen(PORT, () => console.log(`mock API on http://localhost:${PORT}  (OTP is always ${OTP})`));
