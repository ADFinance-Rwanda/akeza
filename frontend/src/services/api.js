import keycloak from '../keycloak.js'

export function userMessage(err) {
  switch (err?.status) {
    case 400: return 'Please check the form and try again.'
    case 401: return 'Your session has expired. Please sign in again.'
    case 403: return "You don't have permission to perform this action."
    case 404: return 'The requested item could not be found.'
    case 409: return 'This item was updated by another user. Refresh and try again.'
    case 429: return 'Too many requests. Please wait a moment and try again.'
    case 503: return 'The service is temporarily unavailable. Please try again shortly.'
    default: return 'Something went wrong. Please try again.'
  }
}

function queryString(params) {
  const search = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '' || value === false) return
    search.set(key, String(value))
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function request(path, { method = 'GET', orgId, body, idempotencyKey } = {}) {
  if (keycloak.authenticated) {
    await keycloak.updateToken(30)
  }
  const headers = { Authorization: `Bearer ${keycloak.token}` }
  if (orgId) headers['X-Organization-Id'] = String(orgId)
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) {
    const err = new Error(userMessage({ status: res.status }))
    err.status = res.status
    throw err
  }
  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  me: () => request('/api/me'),
  dashboard: (orgId) => request('/api/analytics/dashboard', { orgId }),
  organizations: {
    list: () => request('/api/organizations'),
    get: (id) => request(`/api/organizations/${id}`, { orgId: id }),
    create: (body) => request('/api/organizations', { method: 'POST', body }),
    delete: (id) => request(`/api/organizations/${id}`, { method: 'DELETE', orgId: id }),
    updateStatus: (id, status) => request(`/api/organizations/${id}/status`, {
      method: 'PUT', orgId: id, body: { status }
    }),
    members: (id) => request(`/api/organizations/${id}/members`, { orgId: id }),
    addMember: (id, body) => request(`/api/organizations/${id}/members`, {
      method: 'POST', orgId: id, body
    }),
    updateMemberRole: (id, userId, role) => request(`/api/organizations/${id}/members/${userId}`, {
      method: 'PUT', orgId: id, body: { role }
    }),
    auditLogs: (id, params) => request(`/api/organizations/${id}/audit-logs${queryString(params)}`, { orgId: id }),
    deadLetters: (id) => request(`/api/organizations/${id}/jobs/dead-letters`, { orgId: id })
  },
  projects: {
    list: (orgId) => request('/api/projects', { orgId }),
    get: (id, orgId) => request(`/api/projects/${id}`, { orgId }),
    create: (orgId, body) => request('/api/projects', { method: 'POST', orgId, body }),
    update: (id, orgId, body) => request(`/api/projects/${id}`, { method: 'PUT', orgId, body }),
    delete: (id, orgId) => request(`/api/projects/${id}`, { method: 'DELETE', orgId })
  },
  tasks: {
    list: (orgId, params) => request(`/api/tasks${queryString(params)}`, { orgId }),
    byProject: (projectId, orgId, params) => request(`/api/projects/${projectId}/tasks${queryString(params)}`, { orgId }),
    get: (projectId, taskId, orgId) => request(`/api/projects/${projectId}/tasks/${taskId}`, { orgId }),
    create: (projectId, orgId, body, idempotencyKey) => request(`/api/projects/${projectId}/tasks`, {
      method: 'POST', orgId, body, idempotencyKey
    }),
    update: (projectId, taskId, orgId, body) => request(`/api/projects/${projectId}/tasks/${taskId}`, {
      method: 'PUT', orgId, body
    }),
    delete: (projectId, taskId, orgId) => request(`/api/projects/${projectId}/tasks/${taskId}`, {
      method: 'DELETE', orgId
    })
  }
}
