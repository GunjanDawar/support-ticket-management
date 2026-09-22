import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listTickets } from '../api/tickets'
import { TICKET_STATUSES } from '../api/types'

export default function TicketListView() {
  const [tickets, setTickets] = useState([])
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    async function loadTickets() {
      setIsLoading(true)
      setErrorMessage('')
      setTickets([])

      try {
        const result = await listTickets({
          keyword,
          status: status || undefined,
          signal: controller.signal,
        })
        setTickets(result)
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setErrorMessage(error.message || 'Unable to load tickets. Please try again.')
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    loadTickets()
    return () => controller.abort()
  }, [keyword, status])

  function handleSearch(event) {
    event.preventDefault()
    setKeyword(keywordInput.trim())
  }

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

      <form className="ticket-filters" onSubmit={handleSearch}>
        <div className="filter-field search-field">
          <label htmlFor="ticket-keyword">Search tickets</label>
          <div className="search-control">
            <input
              id="ticket-keyword"
              name="keyword"
              type="search"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder="Search title or description"
            />
            <button type="submit" disabled={isLoading}>
              Search
            </button>
          </div>
        </div>

        <div className="filter-field">
          <label htmlFor="ticket-status">Status</label>
          <select
            id="ticket-status"
            name="status"
            value={status}
            disabled={isLoading}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">All</option>
            {TICKET_STATUSES.map((ticketStatus) => (
              <option key={ticketStatus} value={ticketStatus}>
                {formatEnum(ticketStatus)}
              </option>
            ))}
          </select>
        </div>
      </form>

      <div className="ticket-results" aria-live="polite" aria-busy={isLoading}>
        {isLoading && <p className="state-panel">Loading tickets...</p>}

        {!isLoading && errorMessage && (
          <div className="state-panel error-panel" role="alert">
            <p>Unable to load tickets. {errorMessage}</p>
          </div>
        )}

        {!isLoading && !errorMessage && tickets.length === 0 && (
          <p className="state-panel">
            {keyword || status ? 'No matching tickets found.' : 'No tickets found.'}
          </p>
        )}

        {!isLoading && !errorMessage && tickets.length > 0 && (
          <div className="table-scroll">
            <table>
              <caption className="visually-hidden">Support ticket summaries</caption>
              <thead>
                <tr>
                  <th scope="col">Ticket ID</th>
                  <th scope="col">Title</th>
                  <th scope="col">Priority</th>
                  <th scope="col">Assignee</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                  <tr key={ticket.id}>
                    <td>{ticket.id}</td>
                    <td>
                      <Link to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                    </td>
                    <td>{formatEnum(ticket.priority)}</td>
                    <td>{ticket.assignee ?? 'Unassigned'}</td>
                    <td>{formatEnum(ticket.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  )
}

function formatEnum(value) {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}
