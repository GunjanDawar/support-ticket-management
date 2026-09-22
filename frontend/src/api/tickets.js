import { PRIORITIES, TICKET_STATUSES } from './types'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.'

export class ApiError extends Error {
  /**
   * @param {import('./types').ApiErrorResponse} response
   */
  constructor(response) {
    super(response.message)
    this.name = 'ApiError'
    this.code = response.code
    this.status = response.status
    this.path = response.path
  }
}

export class UnexpectedApiError extends Error {
  constructor() {
    super(GENERIC_ERROR_MESSAGE)
    this.name = 'UnexpectedApiError'
  }
}

/**
 * @param {{ keyword?: string, status?: import('./types').TicketStatus, signal?: AbortSignal }} [options]
 * @returns {Promise<import('./types').TicketSummaryResponse[]>}
 */
export async function listTickets({ keyword = '', status, signal } = {}) {
  const query = new URLSearchParams()
  const trimmedKeyword = keyword.trim()

  if (trimmedKeyword) {
    query.set('keyword', trimmedKeyword)
  }
  if (status) {
    query.set('status', status)
  }

  const suffix = query.size ? `?${query.toString()}` : ''
  const response = await request(`${API_BASE_URL}/tickets${suffix}`, {
    headers: { Accept: 'application/json' },
    signal,
  })
  return parseSuccessfulJson(response, isTicketSummaryList)
}

/**
 * @param {import('./types').CreateTicketRequest} ticket
 * @returns {Promise<import('./types').TicketResponse>}
 */
export async function createTicket(ticket) {
  const requestBody = {
    title: ticket.title,
    description: ticket.description,
    priority: ticket.priority,
  }
  if (ticket.assignee !== undefined && ticket.assignee !== '') {
    requestBody.assignee = ticket.assignee
  }

  const response = await request(`${API_BASE_URL}/tickets`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(requestBody),
  })
  return parseSuccessfulJson(response, isTicketResponse)
}

/**
 * @param {string} id
 * @param {{ signal?: AbortSignal }} [options]
 * @returns {Promise<import('./types').TicketDetailResponse>}
 */
export async function getTicket(id, { signal } = {}) {
  const response = await request(`${API_BASE_URL}/tickets/${encodeURIComponent(id)}`, {
    headers: { Accept: 'application/json' },
    signal,
  })
  return parseSuccessfulJson(response, isTicketDetailResponse)
}

/**
 * @param {string} id
 * @param {import('./types').UpdateTicketRequest} changes
 * @returns {Promise<import('./types').TicketResponse>}
 */
export async function updateTicket(id, changes) {
  const requestBody = {}
  for (const field of ['title', 'description', 'priority', 'assignee']) {
    if (Object.prototype.hasOwnProperty.call(changes, field)) {
      requestBody[field] = changes[field]
    }
  }

  const response = await request(`${API_BASE_URL}/tickets/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(requestBody),
  })
  return parseSuccessfulJson(response, isTicketResponse)
}

/**
 * @param {string} id
 * @param {import('./types').TicketStatus} targetStatus
 * @returns {Promise<import('./types').TicketResponse>}
 */
export async function changeTicketStatus(id, targetStatus) {
  const response = await request(
    `${API_BASE_URL}/tickets/${encodeURIComponent(id)}/status`,
    {
      method: 'PATCH',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ targetStatus }),
    },
  )
  return parseSuccessfulJson(response, isTicketResponse)
}

/**
 * @param {string} id
 * @param {string} body
 * @returns {Promise<import('./types').CommentResponse>}
 */
export async function addComment(id, body) {
  const response = await request(
    `${API_BASE_URL}/tickets/${encodeURIComponent(id)}/comments`,
    {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ body }),
    },
  )
  return parseSuccessfulJson(response, isCommentResponse)
}

async function request(url, options) {
  try {
    const response = await fetch(url, options)
    if (!response.ok) {
      throw await toApiError(response)
    }
    return response
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    if (error instanceof ApiError || error instanceof UnexpectedApiError) {
      throw error
    }
    throw new UnexpectedApiError()
  }
}

async function parseSuccessfulJson(response, validator) {
  try {
    const body = await response.json()
    if (!validator(body)) {
      throw new UnexpectedApiError()
    }
    return body
  } catch (error) {
    if (error instanceof UnexpectedApiError) {
      throw error
    }
    throw new UnexpectedApiError()
  }
}

async function toApiError(response) {
  try {
    const body = await response.json()
    if (
      typeof body?.code === 'string' &&
      typeof body.message === 'string' &&
      typeof body.status === 'number' &&
      typeof body.path === 'string'
    ) {
      return new ApiError(body)
    }
  } catch {
    // Unexpected response bodies use the generic frontend error below.
  }
  return new UnexpectedApiError()
}

function isTicketSummaryList(value) {
  return (
    Array.isArray(value) &&
    value.every(
      (ticket) =>
        typeof ticket?.id === 'string' &&
        typeof ticket.title === 'string' &&
        PRIORITIES.includes(ticket.priority) &&
        (ticket.assignee === null || typeof ticket.assignee === 'string') &&
        TICKET_STATUSES.includes(ticket.status),
    )
  )
}

function isTicketResponse(ticket) {
  return (
    typeof ticket?.id === 'string' &&
    typeof ticket.title === 'string' &&
    typeof ticket.description === 'string' &&
    PRIORITIES.includes(ticket.priority) &&
    (ticket.assignee === null || typeof ticket.assignee === 'string') &&
    TICKET_STATUSES.includes(ticket.status)
  )
}

function isTicketDetailResponse(ticket) {
  return (
    isTicketResponse(ticket) &&
    Array.isArray(ticket.comments) &&
    ticket.comments.every(isCommentResponse)
  )
}

function isCommentResponse(comment) {
  return (
    typeof comment?.id === 'string' &&
    typeof comment.body === 'string' &&
    typeof comment.timestamp === 'string' &&
    !Number.isNaN(Date.parse(comment.timestamp))
  )
}
