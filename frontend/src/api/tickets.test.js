import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addComment,
  ApiError,
  changeTicketStatus,
  createTicket,
  getTicket,
  listTickets,
  UnexpectedApiError,
  updateTicket,
} from './tickets'

const summary = {
  id: '9223372036854775807',
  title: 'Login issue',
  priority: 'HIGH',
  assignee: null,
  status: 'OPEN',
}

const ticket = {
  ...summary,
  description: 'Cannot sign in',
}

const detail = {
  ...ticket,
  comments: [
    { id: '84', body: 'Investigating', timestamp: '2026-09-22T15:30:00Z' },
  ],
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn())
})

describe('listTickets', () => {
  it.each([
    [{}, '/api/tickets'],
    [{ keyword: 'login' }, '/api/tickets?keyword=login'],
    [{ status: 'IN_PROGRESS' }, '/api/tickets?status=IN_PROGRESS'],
    [
      { keyword: 'login', status: 'IN_PROGRESS' },
      '/api/tickets?keyword=login&status=IN_PROGRESS',
    ],
    [{ keyword: '', status: undefined }, '/api/tickets'],
  ])('constructs the approved collection request for %o', async (options, expectedUrl) => {
    fetch.mockResolvedValue(jsonResponse([summary]))

    await expect(listTickets(options)).resolves.toEqual([summary])

    expect(fetch).toHaveBeenCalledWith(
      expectedUrl,
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
  })

  it('rejects malformed successful responses', async () => {
    fetch.mockResolvedValue(jsonResponse([{ ...summary, id: 42 }]))
    await expect(listTickets()).rejects.toBeInstanceOf(UnexpectedApiError)
  })

  it('converts contractual, unexpected, and network failures', async () => {
    fetch.mockResolvedValueOnce(
      jsonResponse(
        { code: 'VALIDATION_ERROR', message: 'Invalid query', status: 400, path: '/api/tickets' },
        false,
      ),
    )
    await expect(listTickets()).rejects.toMatchObject({
      name: 'ApiError',
      code: 'VALIDATION_ERROR',
      status: 400,
    })

    fetch.mockResolvedValueOnce(jsonResponse({ error: 'internal' }, false))
    await expect(listTickets()).rejects.toBeInstanceOf(UnexpectedApiError)

    fetch.mockRejectedValueOnce(new TypeError('network unavailable'))
    await expect(listTickets()).rejects.toBeInstanceOf(UnexpectedApiError)
  })
})

describe('createTicket', () => {
  it('posts only approved fields and omits an empty assignee', async () => {
    fetch.mockResolvedValue(jsonResponse(ticket))

    await createTicket({
      title: ticket.title,
      description: ticket.description,
      priority: ticket.priority,
      assignee: '',
      status: 'CLOSED',
    })

    expectRequest('/api/tickets', 'POST', {
      title: ticket.title,
      description: ticket.description,
      priority: ticket.priority,
    })
  })

  it('preserves a supplied assignee and validates the response', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ ...ticket, assignee: 'Alice' }))
    await createTicket({ ...ticket, assignee: 'Alice' })
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      title: ticket.title,
      description: ticket.description,
      priority: ticket.priority,
      assignee: 'Alice',
    })

    fetch.mockResolvedValueOnce(jsonResponse({ ...ticket, status: 'UNKNOWN' }))
    await expect(createTicket(ticket)).rejects.toBeInstanceOf(UnexpectedApiError)
  })
})

describe('detail and mutation operations', () => {
  it('gets detail using the unchanged String ID and converts 404 errors', async () => {
    fetch.mockResolvedValueOnce(jsonResponse(detail))
    await expect(getTicket(summary.id)).resolves.toEqual(detail)
    expect(fetch.mock.calls[0][0]).toBe(`/api/tickets/${summary.id}`)

    fetch.mockResolvedValueOnce(
      jsonResponse(
        { code: 'TICKET_NOT_FOUND', message: 'Missing', status: 404, path: '/api/tickets/9' },
        false,
      ),
    )
    await expect(getTicket('9')).rejects.toBeInstanceOf(ApiError)
  })

  it('patches only approved supplied fields and preserves explicit null', async () => {
    fetch.mockResolvedValue(jsonResponse({ ...ticket, assignee: null }))

    await updateTicket(summary.id, {
      title: 'Updated',
      assignee: null,
      status: 'CLOSED',
      comments: [],
    })

    expectRequest(`/api/tickets/${summary.id}`, 'PATCH', {
      title: 'Updated',
      assignee: null,
    })
  })

  it('uses the dedicated status endpoint with only targetStatus', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ ...ticket, status: 'IN_PROGRESS' }))
    await changeTicketStatus(summary.id, 'IN_PROGRESS')
    expectRequest(`/api/tickets/${summary.id}/status`, 'PATCH', {
      targetStatus: 'IN_PROGRESS',
    })

    fetch.mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'INVALID_STATUS_TRANSITION',
          message: 'Conflict',
          status: 409,
          path: `/api/tickets/${summary.id}/status`,
        },
        false,
      ),
    )
    await expect(changeTicketStatus(summary.id, 'CLOSED')).rejects.toMatchObject({
      code: 'INVALID_STATUS_TRANSITION',
    })
  })

  it('posts a comment body only and converts errors', async () => {
    const comment = { id: summary.id, body: 'Checked logs', timestamp: '2026-09-22T15:30:00Z' }
    fetch.mockResolvedValueOnce(jsonResponse(comment))
    await expect(addComment(summary.id, comment.body)).resolves.toEqual(comment)
    expectRequest(`/api/tickets/${summary.id}/comments`, 'POST', { body: comment.body })

    fetch.mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'TERMINAL_TICKET_CONFLICT',
          message: 'Terminal',
          status: 409,
          path: `/api/tickets/${summary.id}/comments`,
        },
        false,
      ),
    )
    await expect(addComment(summary.id, comment.body)).rejects.toMatchObject({
      code: 'TERMINAL_TICKET_CONFLICT',
    })
  })
})

function jsonResponse(body, ok = true) {
  return {
    ok,
    json: vi.fn().mockResolvedValue(body),
  }
}

function expectRequest(url, method, body) {
  expect(fetch).toHaveBeenCalledWith(
    url,
    expect.objectContaining({
      method,
      body: JSON.stringify(body),
    }),
  )
}
