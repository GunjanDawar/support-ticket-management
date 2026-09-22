/**
 * @typedef {'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'} Priority
 */

/**
 * @typedef {'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'CANCELLED'} TicketStatus
 */

/**
 * @typedef {object} TicketSummaryResponse
 * @property {string} id
 * @property {string} title
 * @property {Priority} priority
 * @property {string | null} assignee
 * @property {TicketStatus} status
 */

/**
 * @typedef {object} CreateTicketRequest
 * @property {string} title
 * @property {string} description
 * @property {Priority} priority
 * @property {string} [assignee]
 */

/**
 * @typedef {object} TicketResponse
 * @property {string} id
 * @property {string} title
 * @property {string} description
 * @property {Priority} priority
 * @property {string | null} assignee
 * @property {TicketStatus} status
 */

/**
 * @typedef {object} CommentResponse
 * @property {string} id
 * @property {string} body
 * @property {string} timestamp
 */

/**
 * @typedef {object} TicketDetailResponse
 * @property {string} id
 * @property {string} title
 * @property {string} description
 * @property {Priority} priority
 * @property {string | null} assignee
 * @property {TicketStatus} status
 * @property {CommentResponse[]} comments
 */

/**
 * @typedef {object} UpdateTicketRequest
 * @property {string} [title]
 * @property {string} [description]
 * @property {Priority} [priority]
 * @property {string | null} [assignee]
 */

/**
 * @typedef {object} ApiErrorResponse
 * @property {string} code
 * @property {string} message
 * @property {number} status
 * @property {string} path
 */

export const PRIORITIES = Object.freeze(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])

export const TICKET_STATUSES = Object.freeze([
  'OPEN',
  'IN_PROGRESS',
  'RESOLVED',
  'CLOSED',
  'CANCELLED',
])
