import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Forbidden, NeedOrg } from '../components/Guards.jsx'
import { formatDateTime } from '../utils/format.js'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api } from '../services/api.js'

export default function Admin() {
  const { orgId, canAdminOrg, organization, members, projects, notifyError, me } = useWorkspace()
  const [jobs, setJobs] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!orgId || !canAdminOrg) return
    let cancelled = false
    setLoading(true)
    api.organizations.deadLetters(orgId)
      .then((list) => { if (!cancelled) setJobs(Array.isArray(list) ? list : []) })
      .catch(notifyError)
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [orgId, canAdminOrg, notifyError])

  if (!canAdminOrg) return <Forbidden />

  return (
    <NeedOrg>
      <div className="page-title">
        <div>
          <h1>Administration</h1>
          <p>Controls available to organization administrators.</p>
        </div>
      </div>
      <div className="admin-sections">
        <section className="card">
          <h2 className="section-title">Organization management</h2>
          <p className="muted">{organization?.name} · {organization?.status}</p>
          <Link to="/organization" className="btn btn-secondary" style={{ marginTop: 8 }}>Open organization</Link>
        </section>
        <section className="card">
          <h2 className="section-title">Member management</h2>
          <p className="muted">{members.length} members. Role changes are limited to Member and Project Manager.</p>
          <Link to="/team" className="btn btn-secondary" style={{ marginTop: 8 }}>Manage team</Link>
        </section>
        <section className="card">
          <h2 className="section-title">Role management</h2>
          <p className="muted">Change member roles from the team page. ORG_ADMIN cannot be assigned from this application.</p>
        </section>
        <section className="card">
          <h2 className="section-title">Audit logs</h2>
          <p className="muted">Review TASK_CREATED, TASK_UPDATED, USER_ROLE_CHANGED and other events.</p>
          <Link to="/audit" className="btn btn-secondary" style={{ marginTop: 8 }}>Open audit logs</Link>
        </section>
        <section className="card">
          <h2 className="section-title">System information</h2>
          <p className="muted">Failed background jobs for this organization. {me?.superAdmin ? 'You are signed in as Super Admin.' : ''}</p>
          <div className="stats-inline" style={{ margin: '12px 0' }}>
            <span><strong>{projects.length}</strong> projects</span>
            <span><strong>{members.length}</strong> members</span>
            <span><strong>{jobs.length}</strong> dead letters</span>
          </div>
          {loading ? <p className="muted">Loading failed jobs...</p> : jobs.length === 0 ? (
            <p className="muted">No failed jobs.</p>
          ) : (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Attempts</th>
                    <th>Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map((job) => (
                    <tr key={job.id}>
                      <td>{job.type}</td>
                      <td>{job.status}</td>
                      <td>{job.attempts}/{job.maxAttempts}</td>
                      <td>{formatDateTime(job.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </NeedOrg>
  )
}
