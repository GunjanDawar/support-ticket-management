import { Link } from 'react-router-dom'

export default function TicketDetailView() {
  return (
    <section aria-labelledby="ticket-detail-heading">
      <p className="eyebrow">Tickets</p>
      <h1 id="ticket-detail-heading">Ticket Detail</h1>
      <div className="placeholder-panel">
        <p>Ticket fields, comments, and approved actions will be implemented in later tasks.</p>
      </div>
      <Link className="text-link" to="/">
        Back to Ticket List
      </Link>
    </section>
  )
}
