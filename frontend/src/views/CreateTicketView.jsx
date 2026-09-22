import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createTicket } from '../api/tickets'
import { PRIORITIES } from '../api/types'

const INITIAL_FORM = {
  title: '',
  description: '',
  priority: '',
  assignee: '',
}

export default function CreateTicketView() {
  const navigate = useNavigate()
  const submittingRef = useRef(false)
  const [form, setForm] = useState(INITIAL_FORM)
  const [errors, setErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    setErrors((current) => ({ ...current, [name]: undefined }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }

    const validationErrors = validate(form)
    setErrors(validationErrors)
    setSubmitError('')
    if (Object.keys(validationErrors).length > 0) {
      return
    }

    submittingRef.current = true
    setIsSubmitting(true)
    try {
      const created = await createTicket(form)
      navigate(`/tickets/${created.id}`)
    } catch (error) {
      setSubmitError(error.message || 'Unable to create the ticket. Please try again.')
    } finally {
      submittingRef.current = false
      setIsSubmitting(false)
    }
  }

  return (
    <section aria-labelledby="create-ticket-heading">
      <p className="eyebrow">Tickets</p>
      <h1 id="create-ticket-heading">Create Ticket</h1>

      <form className="ticket-form" onSubmit={handleSubmit} noValidate>
        {submitError && (
          <div className="state-panel error-panel form-error" role="alert">
            <p>Unable to create the ticket. {submitError}</p>
          </div>
        )}

        <div className="form-field">
          <label htmlFor="ticket-title">
            Title <span aria-hidden="true">*</span>
          </label>
          <input
            id="ticket-title"
            name="title"
            type="text"
            value={form.title}
            required
            aria-required="true"
            aria-invalid={Boolean(errors.title)}
            aria-describedby={errors.title ? 'ticket-title-error' : undefined}
            onChange={updateField}
          />
          {errors.title && (
            <p className="field-error" id="ticket-title-error">
              {errors.title}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="ticket-description">
            Description <span aria-hidden="true">*</span>
          </label>
          <textarea
            id="ticket-description"
            name="description"
            rows="8"
            value={form.description}
            required
            aria-required="true"
            aria-invalid={Boolean(errors.description)}
            aria-describedby={errors.description ? 'ticket-description-error' : undefined}
            onChange={updateField}
          />
          {errors.description && (
            <p className="field-error" id="ticket-description-error">
              {errors.description}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="ticket-priority">
            Priority <span aria-hidden="true">*</span>
          </label>
          <select
            id="ticket-priority"
            name="priority"
            value={form.priority}
            required
            aria-required="true"
            aria-invalid={Boolean(errors.priority)}
            aria-describedby={errors.priority ? 'ticket-priority-error' : undefined}
            onChange={updateField}
          >
            <option value="">Select a priority</option>
            {PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {formatPriority(priority)}
              </option>
            ))}
          </select>
          {errors.priority && (
            <p className="field-error" id="ticket-priority-error">
              {errors.priority}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="ticket-assignee">Assignee (optional)</label>
          <input
            id="ticket-assignee"
            name="assignee"
            type="text"
            value={form.assignee}
            onChange={updateField}
          />
        </div>

        <div className="form-actions">
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating ticket...' : 'Create Ticket'}
          </button>
          <Link className="text-link" to="/">
            Back to Tickets
          </Link>
        </div>
      </form>
    </section>
  )
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

function formatPriority(priority) {
  return priority.charAt(0) + priority.slice(1).toLowerCase()
}
