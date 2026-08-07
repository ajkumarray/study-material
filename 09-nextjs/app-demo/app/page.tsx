// A Server Component (the default): it runs ONLY on the server, can be async, and
// ships zero JS for itself to the client. Great for data fetching + static content.
export default function HomePage() {
  return (
    <section>
      <p>
        This app demonstrates the Next.js App Router: nested layouts, Server
        Components that fetch data, a Client Component island for interactivity,
        a Route Handler (REST endpoint), a dynamic route, and a Server Action.
      </p>
      <p>
        Go to <a href="/expenses">/expenses</a> to see server-side data fetching.
      </p>
    </section>
  );
}
