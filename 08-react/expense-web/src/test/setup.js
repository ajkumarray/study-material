// Adds jest-dom matchers (toBeInTheDocument, toHaveTextContent, ...) to Vitest's
// expect, and cleans up the DOM after each test. Loaded via vite.config's setupFiles.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => cleanup());
