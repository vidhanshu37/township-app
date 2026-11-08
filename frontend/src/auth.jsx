import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, store } from './api.js';

export const ROLE_HOME = { RESIDENT: '/resident', HELP_DESK: '/helpdesk', COMMITTEE: '/committee' };

const AuthContext = createContext(null);

async function loadMe() {
  const me = await api('/api/me');
  return { name: me.name, role: me.role, flatNo: me.flat_no ?? null, tower: me.tower ?? null };
}

export function AuthProvider({ children }) {
  // status: loading (checking a saved session), in, out
  const [state, setState] = useState({ status: 'loading', user: null, notice: null });

  // Bring back a signed-in user after a page refresh.
  useEffect(() => {
    if (!store.read()?.token) {
      setState({ status: 'out', user: null, notice: null });
      return undefined;
    }
    let cancelled = false;
    loadMe()
      .then((user) => !cancelled && setState({ status: 'in', user, notice: null }))
      .catch(() => {
        store.clear();
        if (!cancelled) setState({ status: 'out', user: null, notice: null });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Any 401 from the backend means the token is gone or expired.
  useEffect(() => {
    const onUnauthorized = () => {
      store.clear();
      setState({ status: 'out', user: null, notice: 'Your session ended. Sign in again.' });
    };
    window.addEventListener('township:unauthorized', onUnauthorized);
    return () => window.removeEventListener('township:unauthorized', onUnauthorized);
  }, []);

  const signIn = useCallback(async (loginResult) => {
    store.write({ token: loginResult.accessToken });
    try {
      const user = await loadMe();
      setState({ status: 'in', user, notice: null });
      return user;
    } catch (err) {
      store.clear();
      throw err;
    }
  }, []);

  const signOut = useCallback(() => {
    store.clear();
    setState({ status: 'out', user: null, notice: null });
  }, []);

  const value = useMemo(() => ({ ...state, signIn, signOut }), [state, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
