import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api.js';

// Loads a GET endpoint. reload() refetches without flashing the loading state again.
export function useLoad(path) {
  const [state, setState] = useState({ status: 'loading' });
  const first = useRef(true);
  const load = useCallback(async () => {
    if (first.current) setState({ status: 'loading' });
    try {
      setState({ status: 'ready', data: await api(path) });
    } catch (err) {
      setState({ status: 'error', message: err.message });
    } finally {
      first.current = false;
    }
  }, [path]);
  useEffect(() => {
    first.current = true;
    load();
  }, [load]);
  return [state, load];
}
