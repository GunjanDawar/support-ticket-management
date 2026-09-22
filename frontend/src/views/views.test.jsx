import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addComment,
  ApiError,
  changeTicketStatus,
  createTicket,
  getTicket,
  listTickets,
  updateTicket,
} from '../api/tickets'
import CreateTicketView from './CreateTicketView'
import TicketDetailView from './TicketDetailView'
import TicketListView from './TicketListView'

vi.mock('../api/tickets', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    listTickets: vi.fn(),
    createTicket: vi.fn(),
    getTicket: vi.fn(),
    updateTicket: vi.fn(),
    changeTicketStatus: vi.fn(),
    addComment: vi.fn(),
  }
})

const largeId = '9223372036854775807'
const summary = {
  id: largeId,
  title: 'Login failure',
  priority: 'HIGH',
  assignee: null,
  status: 'OPEN',
}
const detail = {
  ...summary,
  description: 'Cannot sign in',
  comments: [
    { id: '84', body: 'Checked the logs', timestamp: '2026-09-22T15:30:00Z' },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('Ticket List', () => {
  it('shows loading, summaries, null assignee, and String-ID navigation', async () => {
    const pending = deferred()
    listTickets.mockReturnValue(pending.promise)
    const user = userEvent.setup()
    renderList()

    expect(screen.getByText('Loading tickets...')).toBeInTheDocument()
    pending.resolve([summary])

    expect(await screen.findByText('Login failure')).toBeInTheDocument()
    expect(screen.getByText(largeId)).toBeInTheDocument()
    expect(screen.getByText('High')).toBeInTheDocument()
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Login failure' }))
    expect(screen.getByTestId('location')).toHaveTextContent(`/tickets/${largeId}`)
  })

  it('shows empty and safe API error states', async () => {
    listTickets.mockResolvedValueOnce([])
    const { unmount } = renderList()
    expect(await screen.findByText('No tickets found.')).toBeInTheDocument()
    unmount()

    listTickets.mockRejectedValueOnce(new Error('Please try again.'))
    renderList()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load tickets')
  })

  it('sends search, status, combined criteria, and All without status=ALL', async () => {
    listTickets.mockResolvedValue([])
    const user = userEvent.setup()
    renderList()
    await screen.findByText('No tickets found.')

    await user.type(screen.getByLabelText('Search tickets'), 'login')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(listTickets).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'login', status: undefined }),
    ))

    for (const status of ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED']) {
      await user.selectOptions(screen.getByLabelText('Status'), status)
      await waitFor(() => expect(listTickets).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: 'login', status }),
      ))
    }

    await user.selectOptions(screen.getByLabelText('Status'), '')
    await waitFor(() => expect(listTickets).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'login', status: undefined }),
    ))
  })
})

describe('Create Ticket', () => {
  it('renders accessible fields and approved required/max-length validation', async () => {
    const user = userEvent.setup()
    renderCreate()

    expect(screen.getByLabelText(/Title/)).toBeRequired()
    expect(screen.getByLabelText(/Description/)).toBeRequired()
    expect(screen.getByLabelText(/Priority/)).toBeRequired()
    expect(screen.getByLabelText('Assignee (optional)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Tickets' })).toHaveAttribute('href', '/')

    await user.click(screen.getByRole('button', { name: 'Create Ticket' }))
    expect(screen.getByText('Title is required.')).toBeInTheDocument()
    expect(screen.getByText('Description is required.')).toBeInTheDocument()
    expect(screen.getByText('Priority is required.')).toBeInTheDocument()
    expect(createTicket).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText(/Title/), { target: { value: 'x'.repeat(201) } })
    fireEvent.change(screen.getByLabelText(/Description/), {
      target: { value: 'x'.repeat(5001) },
    })
    await user.selectOptions(screen.getByLabelText(/Priority/), 'HIGH')
    await user.click(screen.getByRole('button', { name: 'Create Ticket' }))
    expect(screen.getByText(/200 characters or fewer/)).toBeInTheDocument()
    expect(screen.getByText(/5000 characters or fewer/)).toBeInTheDocument()
  })

  it('submits exact entered values, allows whitespace, and navigates with returned String ID', async () => {
    createTicket.mockResolvedValue({ ...detail, comments: undefined })
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText(/Title/), '   ')
    await user.type(screen.getByLabelText(/Description/), '  description  ')
    await user.selectOptions(screen.getByLabelText(/Priority/), 'MEDIUM')
    await user.type(screen.getByLabelText('Assignee (optional)'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'Create Ticket' }))

    expect(createTicket).toHaveBeenCalledWith({
      title: '   ',
      description: '  description  ',
      priority: 'MEDIUM',
      assignee: 'not-an-email',
    })
    expect(await screen.findByTestId('location')).toHaveTextContent(`/tickets/${largeId}`)
  })

  it('prevents duplicate submission and preserves values after failure', async () => {
    const pending = deferred()
    createTicket.mockReturnValue(pending.promise)
    const user = userEvent.setup()
    renderCreate()
    await fillCreateForm(user)

    const submit = screen.getByRole('button', { name: 'Create Ticket' })
    await user.click(submit)
    expect(screen.getByRole('button', { name: 'Creating ticket...' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Creating ticket...' }))
    expect(createTicket).toHaveBeenCalledTimes(1)

    pending.reject(new Error('Request failed safely.'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Request failed safely')
    expect(screen.getByLabelText(/Title/)).toHaveValue('A ticket')
  })
})

describe('Ticket Detail and mutations', () => {
  it('shows loading, all detail fields, comments, and navigation', async () => {
    const pending = deferred()
    getTicket.mockReturnValue(pending.promise)
    renderDetail()
    expect(screen.getByText('Loading ticket...')).toBeInTheDocument()
    pending.resolve(detail)

    expect(await screen.findByText(detail.title)).toBeInTheDocument()
    expect(screen.getByText(detail.description)).toBeInTheDocument()
    expect(screen.getByText('Checked the logs')).toBeInTheDocument()
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Tickets' })).toHaveAttribute('href', '/')
  })

  it('shows no-comments, not-found, and unexpected error states', async () => {
    getTicket.mockResolvedValueOnce({ ...detail, comments: [] })
    const { unmount } = renderDetail()
    expect(await screen.findByText('No comments yet.')).toBeInTheDocument()
    unmount()

    getTicket.mockRejectedValueOnce(apiError('TICKET_NOT_FOUND', 404))
    const second = renderDetail()
    expect(await screen.findByText('Ticket not found.')).toBeInTheDocument()
    second.unmount()

    getTicket.mockRejectedValueOnce(new Error('Please try again.'))
    renderDetail()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load ticket')
  })

  it.each(['OPEN', 'IN_PROGRESS', 'RESOLVED'])('allows editing in %s', async (status) => {
    getTicket.mockResolvedValue({ ...detail, status })
    renderDetail()
    expect(await screen.findByRole('button', { name: 'Edit Ticket' })).toBeInTheDocument()
  })

  it.each(['CLOSED', 'CANCELLED'])(
    'makes %s tickets read-only while retaining comments',
    async (status) => {
      getTicket.mockResolvedValue({ ...detail, status })
      renderDetail()
      expect(await screen.findByText('Checked the logs')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Edit Ticket' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add Comment' })).not.toBeInTheDocument()
      expect(screen.getByText(/No status actions are available/)).toBeInTheDocument()
    },
  )

  it('sends changed fields only, accepts server values, and supports cancel', async () => {
    getTicket
      .mockResolvedValueOnce(detail)
      .mockResolvedValue({ ...detail, title: 'Server title' })
    updateTicket.mockResolvedValue({ ...detail, title: 'Server title' })
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Edit Ticket' }))
    const title = screen.getByLabelText('Title')
    await user.clear(title)
    await user.type(title, 'Client title')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    expect(updateTicket).toHaveBeenCalledWith(largeId, { title: 'Client title' })
    expect(await screen.findByText('Server title')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Edit Ticket' }))
    await user.clear(screen.getByLabelText('Title'))
    await user.type(screen.getByLabelText('Title'), 'Discard me')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(updateTicket).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Server title')).toBeInTheDocument()
  })

  it('clears assignee with explicit null and preserves failed edit input', async () => {
    getTicket.mockResolvedValue({ ...detail, assignee: 'Alice' })
    updateTicket.mockRejectedValue(new Error('Update failed safely.'))
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Edit Ticket' }))
    await user.clear(screen.getByLabelText('Assignee (optional)'))
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    expect(updateTicket).toHaveBeenCalledWith(largeId, { assignee: null })
    expect(await screen.findByRole('alert')).toHaveTextContent('Update failed safely')
    expect(screen.getByLabelText('Assignee (optional)')).toHaveValue('')
  })

  it('enforces only approved edit validation boundaries', async () => {
    getTicket.mockResolvedValue(detail)
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Edit Ticket' }))
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'x'.repeat(201) } })
    fireEvent.change(screen.getByLabelText('Description'), {
      target: { value: 'x'.repeat(5001) },
    })
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))
    expect(screen.getByText(/Title must be 200/)).toBeInTheDocument()
    expect(screen.getByText(/Description must be 5000/)).toBeInTheDocument()
    expect(updateTicket).not.toHaveBeenCalled()
  })

  it.each([
    ['OPEN', ['Move to In Progress', 'Cancel']],
    ['IN_PROGRESS', ['Resolve', 'Cancel']],
    ['RESOLVED', ['Close']],
  ])('shows documented %s status actions', async (status, labels) => {
    getTicket.mockResolvedValue({ ...detail, status })
    renderDetail()
    for (const label of labels) {
      expect(await screen.findByRole('button', { name: label })).toBeInTheDocument()
    }
  })

  it.each([
    ['OPEN', 'Move to In Progress', 'IN_PROGRESS'],
    ['OPEN', 'Cancel', 'CANCELLED'],
    ['IN_PROGRESS', 'Resolve', 'RESOLVED'],
    ['IN_PROGRESS', 'Cancel', 'CANCELLED'],
    ['RESOLVED', 'Close', 'CLOSED'],
  ])('maps %s %s to %s through the API', async (source, label, target) => {
    const sourceTicket = { ...detail, status: source }
    const targetTicket = { ...detail, status: target }
    getTicket.mockResolvedValueOnce(sourceTicket).mockResolvedValue(targetTicket)
    changeTicketStatus.mockResolvedValue(targetTicket)
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: label }))
    expect(changeTicketStatus).toHaveBeenCalledWith(largeId, target)
  })

  it('uses dedicated status mutation without optimism and refreshes stale conflicts', async () => {
    const stale = { ...detail, status: 'OPEN' }
    const refreshed = { ...detail, status: 'RESOLVED' }
    getTicket.mockResolvedValueOnce(stale).mockResolvedValueOnce(refreshed)
    changeTicketStatus.mockRejectedValue(apiError('INVALID_STATUS_TRANSITION', 409))
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Move to In Progress' }))

    expect(changeTicketStatus).toHaveBeenCalledWith(largeId, 'IN_PROGRESS')
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer allowed')
    expect(getTicket).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('button', { name: 'Close' })).toBeInTheDocument()
  })

  it('prevents duplicate status submissions while the transition is pending', async () => {
    getTicket.mockResolvedValue(detail)
    const pending = deferred()
    changeTicketStatus.mockReturnValue(pending.promise)
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Move to In Progress' }))

    const pendingButtons = screen.getAllByRole('button', { name: 'Updating status...' })
    expect(pendingButtons.every((button) => button.disabled)).toBe(true)
    await user.click(pendingButtons[0])
    expect(changeTicketStatus).toHaveBeenCalledTimes(1)
  })

  it('validates, submits, refreshes, and preserves failed comments', async () => {
    const refreshed = {
      ...detail,
      comments: [...detail.comments, { id: '85', body: 'New comment', timestamp: '2026-09-22T16:00:00Z' }],
    }
    getTicket.mockResolvedValueOnce(detail).mockResolvedValueOnce(refreshed)
    addComment.mockResolvedValue({ id: '85', body: 'New comment', timestamp: '2026-09-22T16:00:00Z' })
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText(detail.title)
    await user.click(screen.getByRole('button', { name: 'Add Comment' }))
    expect(screen.getByText('Comment is required.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Add a comment'), 'New comment')
    await user.click(screen.getByRole('button', { name: 'Add Comment' }))
    expect(addComment).toHaveBeenCalledWith(largeId, 'New comment')
    expect(await screen.findByText('New comment')).toBeInTheDocument()
    expect(screen.getByLabelText('Add a comment')).toHaveValue('')
  })

  it('prevents duplicate comments and preserves input while pending', async () => {
    getTicket.mockResolvedValue(detail)
    const pending = deferred()
    addComment.mockReturnValue(pending.promise)
    const user = userEvent.setup()
    renderDetail()
    const input = await screen.findByLabelText('Add a comment')
    await user.type(input, 'Pending comment')
    await user.click(screen.getByRole('button', { name: 'Add Comment' }))

    const submit = screen.getByRole('button', { name: 'Adding comment...' })
    expect(submit).toBeDisabled()
    await user.click(submit)
    expect(addComment).toHaveBeenCalledTimes(1)
    expect(input).toHaveValue('Pending comment')
  })

  it('preserves comment text and shows a safe failure', async () => {
    getTicket.mockResolvedValue(detail)
    addComment.mockRejectedValue(new Error('Unable to add comment safely.'))
    const user = userEvent.setup()
    renderDetail()
    const input = await screen.findByLabelText('Add a comment')
    await user.type(input, 'Keep this comment')
    await user.click(screen.getByRole('button', { name: 'Add Comment' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to add comment safely')
    expect(input).toHaveValue('Keep this comment')
  })

  it('refreshes terminal comment conflicts and removes mutation controls', async () => {
    getTicket
      .mockResolvedValueOnce(detail)
      .mockResolvedValueOnce({ ...detail, status: 'CLOSED' })
    addComment.mockRejectedValue(apiError('TERMINAL_TICKET_CONFLICT', 409))
    const user = userEvent.setup()
    renderDetail()
    await user.type(await screen.findByLabelText('Add a comment'), 'Keep this text')
    await user.click(screen.getByRole('button', { name: 'Add Comment' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('terminal')
    expect(getTicket).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('button', { name: 'Edit Ticket' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add Comment' })).not.toBeInTheDocument()
  })
})

function renderList() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<TicketListView />} />
        <Route path="/tickets/:ticketId" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  )
}

function renderCreate() {
  return render(
    <MemoryRouter initialEntries={['/tickets/new']}>
      <Routes>
        <Route path="/tickets/new" element={<CreateTicketView />} />
        <Route path="/tickets/:ticketId" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  )
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={[`/tickets/${largeId}`]}>
      <Routes>
        <Route path="/tickets/:ticketId" element={<TicketDetailView />} />
      </Routes>
    </MemoryRouter>,
  )
}

function Location() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

async function fillCreateForm(user) {
  await user.type(screen.getByLabelText(/Title/), 'A ticket')
  await user.type(screen.getByLabelText(/Description/), 'A description')
  await user.selectOptions(screen.getByLabelText(/Priority/), 'HIGH')
}

function apiError(code, status) {
  return new ApiError({ code, message: 'Safe error', status, path: '/api/tickets/1' })
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
