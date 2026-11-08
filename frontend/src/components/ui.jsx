export function Notice({ kind = 'info', children }) {
  return (
    <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function Loading({ children }) {
  return <p className="muted" role="status">{children}</p>;
}
