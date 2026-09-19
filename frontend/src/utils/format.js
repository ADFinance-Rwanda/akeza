export function displayName(user) {
  if (!user) return 'User'
  const name = [user.firstName, user.lastName].filter(Boolean).join(' ').trim()
  return name || user.email || 'User'
}

export function initials(user) {
  const name = displayName(user)
  const parts = name.split(/[\s@]+/).filter(Boolean)
  return ((parts[0]?.[0] || 'U') + (parts[1]?.[0] || '')).toUpperCase()
}

export function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

export function formatDateTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function roleLabel(role) {
  return ({
    SUPER_ADMIN: 'Super Admin',
    ORG_ADMIN: 'Org Admin',
    PROJECT_MANAGER: 'Project Manager',
    MEMBER: 'Member'
  })[role] || role || 'Member'
}

export function statusLabel(status) {
  return ({ TODO: 'To do', IN_PROGRESS: 'In progress', DONE: 'Done', ACTIVE: 'Active', ARCHIVED: 'Archived', SUSPENDED: 'Suspended' })[status] || status
}

export function badgeClass(kind, value) {
  const v = String(value || '').toUpperCase()
  if (kind === 'status') {
    if (v === 'TODO') return 'badge todo'
    if (v === 'IN_PROGRESS') return 'badge progress'
    if (v === 'DONE') return 'badge done'
    if (v === 'ACTIVE') return 'badge active'
    if (v === 'ARCHIVED' || v === 'SUSPENDED') return 'badge archived'
  }
  if (kind === 'priority') {
    if (v === 'LOW') return 'badge low'
    if (v === 'MEDIUM') return 'badge medium'
    if (v === 'HIGH') return 'badge high'
    if (v === 'CRITICAL') return 'badge critical'
  }
  if (kind === 'role') return 'badge admin'
  return 'badge'
}

export function isOverdue(task) {
  if (!task?.dueDate || task.status === 'DONE') return false
  const today = new Date()
  const due = new Date(`${task.dueDate}T00:00:00Z`)
  return due < new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()))
}

export function memberName(members, userId) {
  if (!userId) return 'System'
  const m = members.find((x) => String(x.userId) === String(userId))
  return m?.userEmail || `User #${userId}`
}

export function projectName(projects, projectId) {
  return projects.find((p) => String(p.id) === String(projectId))?.name || `Project #${projectId}`
}
