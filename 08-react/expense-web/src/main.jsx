import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App.jsx';
import './index.css';

/*
 * The entry point. createRoot mounts the React tree into #root (index.html).
 * StrictMode is a dev-only wrapper that surfaces bugs (it double-invokes some
 * functions to catch impure renders/missing effect cleanup) — no effect in prod.
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>
);
