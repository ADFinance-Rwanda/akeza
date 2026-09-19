import { useEffect, useMemo, useState } from 'react'
import { Forbidden, NeedOrg } from '../components/Guards.jsx'
import { EmptyState, Field, Pagination } from '../components/Common.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api } from '../services/api.js'
import { formatDateTime, memberName } from '../utils/format.js'

export default function AuditLogs() {
  const { orgId, members, canAdminOrg, organization, notifyError } = useWorkspace()
  const [page, setPage] = useState(0)
  const [raw, setRaw] = useState({ content: [], totalPages: 0 })
  const [loading, setLoading] = useState(false)
  const [q, setQ] = useState('')
  const [action, setAction] = useState('')
  const [date, setDate] = useState('')

  useEffect(() => {
    if (!orgId || !canAdminOrg) return
    let cancelled = false
    setLoading(true)
    api.organizations.auditLogs(orgId, { page, size: 20 })
      .then((res) => { if (!cancelled) setRaw(res) })
      .catch(notifyError)
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [orgId, page, canAdminOrg, notifyError])

  const rows = useMemo(() => (raw.content || []).filter((row) => {
    const hay = `${row.action} ${row.entityType} ${row.entityId} ${row.metadata || ''}`.toLowerCase()
    if (q && !hay.includes(q.toLowerCase())) return false
    if (action && row.action !== action) return false
    if (date && String(row.createdAt).slice(0, 10) !== date) return false
    return true
  }), [raw, q, action, date])

  const actions = [...new Set((raw.content || []).map((r) => r.action))].sort()

  if (!canAdminOrg) return <Forbidden />

  return (
    <NeedOrg>
      <div className="page-title">
        <div>
          <h1>Audit Logs</h1>
          <p>Read-only history for {organization?.name || 'this organization'}.</p>
        </div>
      </div>
      <form className="toolbar" onSubmit={(e) => e.preventDefault()}>
        <div className="grow">
          <label className="sr-only" htmlFor="audit-search">Search</label>
          <input id="audit-search" className="input" placeholder="Search" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <Field id="audit-action" label="Action">
          <select id="audit-action" className="select" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All actions</option>
            {actions.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </Field>
        <Field id="audit-date" label="Date">
          <input id="audit-date" className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
      </form>
      {loading ? <p className="muted">Loading audit logs...</p> : rows.length === 0 ? (
        <EmptyState title="No audit entries match your filters." />
      ) : (
        <div className="table-wrap card" style={{ padding: 0 }}>
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
                  <td>{memberName(members, row.userId)}</td>
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
