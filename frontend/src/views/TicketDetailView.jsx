import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, getTicket, updateTicket } from '../api/tickets'
import { PRIORITIES } from '../api/types'

export default function TicketDetailView() {
  const { ticketId } = useParams()
  const mountedRef = useRef(true)
  const savingRef = useRef(false)
  const [ticket, setTicket] = useState(null)
  const [loadState, setLoadState] = useState('loading')
  const [loadError, setLoadError] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [form, setForm] = useState(null)
  const [errors, setErrors] = useState({})
  const [updateError, setUpdateError] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    mountedRef.current = true
    const controller = new AbortController()

    async function load() {
      setLoadState('loading')
      setLoadError('')
      setTicket(null)
      setIsEditing(false)

      try {
        const result = await getTicket(ticketId, { signal: controller.signal })
        setTicket(result)
        setLoadState('ready')
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        if (error instanceof ApiError && error.code === 'TICKET_NOT_FOUND') {
          setLoadState('notFound')
        } else {
          setLoadError(error.message || 'Unable to load ticket. Please try again.')
          setLoadState('error')
        }
      }
    }

    load()
    return () => {
      mountedRef.current = false
      controller.abort()
    }
  }, [ticketId])

  function beginEdit() {
    setForm(formFromTicket(ticket))
    setErrors({})
    setUpdateError('')
    setIsEditing(true)
  }

  function cancelEdit() {
    setForm(null)
    setErrors({})
    setUpdateError('')
    setIsEditing(false)
  }

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    setErrors((current) => ({ ...current, [name]: undefined }))
  }

  async function handleSave(event) {
    event.preventDefault()
    if (savingRef.current) {
      return
    }

    const validationErrors = validate(form)
    setErrors(validationErrors)
    setUpdateError('')
    if (Object.keys(validationErrors).length > 0) {
      return
    }

    const changes = changedFields(ticket, form)
    if (Object.keys(changes).length === 0) {
      cancelEdit()
      return
    }

    savingRef.current = true
    setIsSaving(true)
    try {
      const updated = await updateTicket(ticket.id, changes)
      if (!mountedRef.current) {
        return
      }
      setTicket((current) => ({ ...current, ...updated }))
      setIsEditing(false)
      setForm(null)

      const refreshed = await getTicket(ticket.id)
      if (mountedRef.current) {
        setTicket(refreshed)
      }
    } catch (error) {
      if (!mountedRef.current) {
        return
      }
      if (error instanceof ApiError && error.code === 'TICKET_NOT_FOUND') {
        setTicket(null)
        setIsEditing(false)
        setLoadState('notFound')
      } else if (
        error instanceof ApiError &&
        error.code === 'TERMINAL_TICKET_CONFLICT'
      ) {
        setUpdateError('This ticket is terminal and can no longer be edited.')
        await refreshAfterConflict(ticket.id, mountedRef, setTicket, setIsEditing)
      } else {
        setUpdateError(error.message || 'Unable to update the ticket. Please try again.')
      }
    } finally {
      savingRef.current = false
      if (mountedRef.current) {
        setIsSaving(false)
      }
    }
  }

  if (loadState === 'loading') {
    return <DetailState message="Loading ticket..." />
  }

  if (loadState === 'notFound') {
    return <DetailState message="Ticket not found." />
  }

  if (loadState === 'error') {
    return <DetailState message={`Unable to load ticket. ${loadError}`} isError />
  }

  const terminal = ticket.status === 'CLOSED' || ticket.status === 'CANCELLED'

  return (
    <section aria-labelledby="ticket-detail-heading">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Tickets</p>
          <h1 id="ticket-detail-heading">Ticket Detail</h1>
        </div>
        {!terminal && !isEditing && (
          <button type="button" onClick={beginEdit}>
            Edit Ticket
          </button>
        )}
      </div>

      {!isEditing && updateError && (
        <div className="state-panel error-panel" role="alert">
          <p>{updateError}</p>
        </div>
      )}

      {isEditing ? (
        <EditForm
          ticket={ticket}
          form={form}
          errors={errors}
          updateError={updateError}
          isSaving={isSaving}
          hasChanges={Object.keys(changedFields(ticket, form)).length > 0}
          onChange={updateField}
          onSave={handleSave}
          onCancel={cancelEdit}
        />
      ) : (
        <TicketFields ticket={ticket} />
      )}

      <section className="comments-section" aria-labelledby="comments-heading">
        <h2 id="comments-heading">Comments</h2>
        {ticket.comments.length === 0 ? (
          <p className="state-panel">No comments yet.</p>
        ) : (
          <ul className="comment-list">
            {ticket.comments.map((comment) => (
              <li key={comment.id}>
                <p>{comment.body}</p>
                <time dateTime={comment.timestamp}>{formatTimestamp(comment.timestamp)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Link className="text-link" to="/">
        Back to Tickets
      </Link>
    </section>
  )
}

function DetailState({ message, isError = false }) {
  return (
    <section aria-labelledby="ticket-detail-heading">
      <p className="eyebrow">Tickets</p>
      <h1 id="ticket-detail-heading">Ticket Detail</h1>
      <div className={`state-panel${isError ? ' error-panel' : ''}`} role={isError ? 'alert' : undefined}>
        <p>{message}</p>
      </div>
      <Link className="text-link" to="/">
        Back to Tickets
      </Link>
    </section>
  )
}

function TicketFields({ ticket }) {
  return (
    <dl className="ticket-detail">
      <div>
        <dt>ID</dt>
        <dd>{ticket.id}</dd>
      </div>
      <div>
        <dt>Title</dt>
        <dd>{ticket.title}</dd>
      </div>
      <div>
        <dt>Description</dt>
        <dd className="preserve-whitespace">{ticket.description}</dd>
      </div>
      <div>
        <dt>Priority</dt>
        <dd>{formatEnum(ticket.priority)}</dd>
      </div>
      <div>
        <dt>Assignee</dt>
        <dd>{ticket.assignee ?? 'Unassigned'}</dd>
      </div>
      <div>
        <dt>Status</dt>
        <dd>{formatEnum(ticket.status)}</dd>
      </div>
    </dl>
  )
}

function EditForm({
  ticket,
  form,
  errors,
  updateError,
  isSaving,
  hasChanges,
  onChange,
  onSave,
  onCancel,
}) {
  return (
    <form className="ticket-form" onSubmit={onSave} noValidate>
      {updateError && (
        <div className="state-panel error-panel form-error" role="alert">
          <p>{updateError}</p>
        </div>
      )}

      <dl className="read-only-fields">
        <div>
          <dt>ID</dt>
          <dd>{ticket.id}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{formatEnum(ticket.status)}</dd>
        </div>
      </dl>

      <div className="form-field">
        <label htmlFor="edit-ticket-title">Title</label>
        <input
          id="edit-ticket-title"
          name="title"
          value={form.title}
          required
          aria-required="true"
          aria-invalid={Boolean(errors.title)}
          aria-describedby={errors.title ? 'edit-ticket-title-error' : undefined}
          onChange={onChange}
        />
        {errors.title && (
          <p className="field-error" id="edit-ticket-title-error">
            {errors.title}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="edit-ticket-description">Description</label>
        <textarea
          id="edit-ticket-description"
          name="description"
          rows="8"
          value={form.description}
          required
          aria-required="true"
          aria-invalid={Boolean(errors.description)}
          aria-describedby={
            errors.description ? 'edit-ticket-description-error' : undefined
          }
          onChange={onChange}
        />
        {errors.description && (
          <p className="field-error" id="edit-ticket-description-error">
            {errors.description}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="edit-ticket-priority">Priority</label>
        <select
          id="edit-ticket-priority"
          name="priority"
          value={form.priority}
          required
          aria-required="true"
          aria-invalid={Boolean(errors.priority)}
          aria-describedby={errors.priority ? 'edit-ticket-priority-error' : undefined}
          onChange={onChange}
        >
          {PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {formatEnum(priority)}
            </option>
          ))}
        </select>
        {errors.priority && (
          <p className="field-error" id="edit-ticket-priority-error">
            {errors.priority}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="edit-ticket-assignee">Assignee (optional)</label>
        <input
          id="edit-ticket-assignee"
          name="assignee"
          value={form.assignee}
          onChange={onChange}
        />
      </div>

      <div className="form-actions">
        <button type="submit" disabled={isSaving || !hasChanges}>
          {isSaving ? 'Saving...' : 'Save Changes'}
        </button>
        <button className="secondary-button" type="button" disabled={isSaving} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function formFromTicket(ticket) {
  return {
    title: ticket.title,
    description: ticket.description,
    priority: ticket.priority,
    assignee: ticket.assignee ?? '',
  }
}

function changedFields(ticket, form) {
  if (!ticket || !form) {
    return {}
  }

  const changes = {}
  if (form.title !== ticket.title) {
    changes.title = form.title
  }
  if (form.description !== ticket.description) {
    changes.description = form.description
  }
  if (form.priority !== ticket.priority) {
    changes.priority = form.priority
  }

  const originalAssignee = ticket.assignee ?? ''
  if (form.assignee !== originalAssignee) {
    changes.assignee = form.assignee === '' ? null : form.assignee
  }
  return changes
}

function validate(form) {
  const errors = {}
  if (form.title.length === 0) {
    errors.title = 'Title is required.'
  } else if (form.title.length > 200) {
    errors.title = 'Title must be 200 characters or fewer.'
  }
  if (form.description.length === 0) {
    errors.description = 'Description is required.'
  } else if (form.description.length > 5000) {
    errors.description = 'Description must be 5000 characters or fewer.'
  }
  if (!PRIORITIES.includes(form.priority)) {
    errors.priority = 'Priority is required.'
  }
  return errors
}

async function refreshAfterConflict(id, mountedRef, setTicket, setIsEditing) {
  try {
    const refreshed = await getTicket(id)
    if (!mountedRef.current) {
      return
    }
    setTicket(refreshed)
    if (refreshed.status === 'CLOSED' || refreshed.status === 'CANCELLED') {
      setIsEditing(false)
    }
  } catch {
    // Keep the existing safe conflict message if the refresh also fails.
  }
}

function formatEnum(value) {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function formatTimestamp(timestamp) {
  return new Date(timestamp).toLocaleString()
}
