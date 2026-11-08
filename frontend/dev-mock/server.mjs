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
let complaintSeq = 0;

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

  return send(res, 404, { status: 404, error: 'Not Found' });
}).listen(PORT, () => console.log(`mock API on http://localhost:${PORT}  (OTP is always ${OTP})`));
