import type { ReactNode } from "react";

// The root layout is REQUIRED in the App Router. It renders once and wraps all
// nested routes; it's a Server Component by default (no "use client").
export const metadata = {
  title: "Next.js Expense Demo",
  description: "App Router: server components, route handlers, server actions",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui", maxWidth: 640, margin: "2rem auto" }}>
        <header>
          <h1>Expenses</h1>
          <nav style={{ display: "flex", gap: "1rem" }}>
            <a href="/">Home</a>
            <a href="/expenses">All expenses</a>
          </nav>
          <hr />
        </header>
        <main>{children}</main>
      </body>
    </html>
  );
}
