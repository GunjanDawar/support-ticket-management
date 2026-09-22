import { Link, Outlet } from 'react-router-dom'

export default function AppLayout() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link className="brand" to="/">
          Support Ticket Management
        </Link>
        <nav aria-label="Primary navigation">
          <Link to="/">Tickets</Link>
          <Link to="/tickets/new">Create Ticket</Link>
        </nav>
      </header>
      <main className="page-content">
        <Outlet />
      </main>
    </div>
  )
}
