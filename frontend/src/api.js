// One place that talks to the backend. The token lives in sessionStorage, so closing the tab signs you out.
const KEY = 'township.session';
let memory = null; // fallback when sessionStorage is blocked (private mode, strict browsers)

export const store = {
  read() {
    if (memory) return memory;
    try {
      return JSON.parse(sessionStorage.getItem(KEY));
    } catch {
      return null;
    }
  },
  write(value) {
    memory = value;
    try {
      sessionStorage.setItem(KEY, JSON.stringify(value));
    } catch {
      /* keep the in-memory copy */
    }
  },
  clear() {
    memory = null;
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* nothing to clear */
    }
  },
};

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

// The backend sends a short "message" for errors we raise ourselves. Anything else gets plain wording.
function messageFor(status, data) {
  const own = data && typeof data.message === 'string' && data.message ? data.message : null;
  if (status === 400) return own || 'Check the details you entered and try again.';
  if (status === 401) return 'Your session has ended. Sign in again.';
  if (status === 403) return "You don't have access to this.";
  if (status === 404) return own || "We couldn't find that.";
  if (status === 409) return own || 'That is no longer possible. Refresh and check the latest status.';
  if (status === 429) return own || 'Too many tries. Wait a little and try again.';
  if (status >= 500) return 'Something went wrong on our side. Try again in a moment.';
  return own || 'Something went wrong. Try again.';
}

export async function api(path, { method = 'GET', body, formData, auth = true } = {}) {
  const headers = {};
  const session = store.read();
  if (auth && session?.token) headers.Authorization = `Bearer ${session.token}`;

  let payload;
  if (formData) {
    payload = formData; // the browser sets the multipart boundary itself
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, "Can't reach the server. Check your connection and try again.");
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (res.ok) return data;

  if (res.status === 401 && auth) window.dispatchEvent(new Event('township:unauthorized'));
  throw new ApiError(res.status, messageFor(res.status, data), data);
}
