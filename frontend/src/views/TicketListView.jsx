import { Link } from 'react-router-dom'

export default function TicketListView() {
  return (
    <section aria-labelledby="ticket-list-heading">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Tickets</p>
          <h1 id="ticket-list-heading">Ticket List</h1>
        </div>
        <Link className="primary-link" to="/tickets/new">
          Create Ticket
        </Link>
      </div>
      <div className="placeholder-panel">
        <p>Ticket summaries, search, and status filtering will be implemented in IMP-020.</p>
      </div>
    </section>
  )
}
