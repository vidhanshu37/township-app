import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/archivo/wdth.css';
import './styles.css';
import App from './App.jsx';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Only in the built app. In development a cached shell would hide your changes.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* the app works fine without it */
    });
  });
}
