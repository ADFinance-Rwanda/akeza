import { useEffect, useState } from 'react'
import { Forbidden, NeedOrg } from '../components/Guards.jsx'
import { EmptyState, Field, Pagination } from '../components/Common.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api } from '../services/api.js'
import { formatDateTime, memberName } from '../utils/format.js'

const AUDIT_ACTIONS = [
  'TASK_CREATED',
  'TASK_UPDATED',
  'TASK_DELETED',
  'PROJECT_CREATED',
  'PROJECT_UPDATED',
  'PROJECT_DELETED',
  'USER_ROLE_CHANGED',
  'MEMBER_ADDED',
  'ORGANIZATION_CREATED',
  'ORGANIZATION_DELETED',
  'ORGANIZATION_STATUS_CHANGED',
  'TASK_OVERDUE',
  'ACCESS_DENIED'
]

export default function AuditLogs() {
  const { orgId, members, canAdminOrg, organization, notifyError } = useWorkspace()
  const [page, setPage] = useState(0)
  const [raw, setRaw] = useState({ content: [], totalPages: 0 })
  const [loading, setLoading] = useState(false)
  const [q, setQ] = useState('')
  const [action, setAction] = useState('')
  const [date, setDate] = useState('')
  const [applied, setApplied] = useState({ q: '', action: '', date: '' })

  useEffect(() => {
    if (!orgId || !canAdminOrg) return
    let cancelled = false
    setLoading(true)
    api.organizations.auditLogs(orgId, {
      page,
      size: 20,
      q: applied.q,
      action: applied.action,
      date: applied.date
    })
      .then((res) => { if (!cancelled) setRaw(res) })
      .catch(notifyError)
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [orgId, page, canAdminOrg, notifyError, applied])

  function applyFilters(e) {
    e.preventDefault()
    setPage(0)
    setApplied({ q: q.trim(), action, date })
  }

  if (!canAdminOrg) return <Forbidden />

  const rows = raw.content || []

  return (
    <NeedOrg>
      <div className="page-title">
        <div>
          <h1>Audit Logs</h1>
          <p>Read-only history for {organization?.name || 'this organization'}.</p>
        </div>
      </div>
      <form className="toolbar" onSubmit={applyFilters}>
        <div className="grow">
          <label className="sr-only" htmlFor="audit-search">Search</label>
          <input id="audit-search" className="input" placeholder="Search action, entity, or metadata" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <Field id="audit-action" label="Action">
          <select id="audit-action" className="select" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All actions</option>
            {AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </Field>
        <Field id="audit-date" label="Date">
          <input id="audit-date" className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <button type="submit" className="btn btn-secondary">Apply</button>
      </form>
      {loading ? <p className="muted">Loading audit logs...</p> : rows.length === 0 ? (
        <EmptyState title="No audit entries match your filters." />
      ) : (
        <div className="table-wrap card table-card">
          <table className="data">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Entity</th>
                <th>Target</th>
                <th>Organization</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{formatDateTime(row.createdAt)}</td>
                  <td>{memberName(members, row.userId, 'System')}</td>
                  <td>{row.action}</td>
                  <td>{row.entityType}</td>
                  <td>{row.entityId}</td>
                  <td>{organization?.name || row.organizationId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={raw.totalPages} onChange={setPage} />
    </NeedOrg>
  )
}
