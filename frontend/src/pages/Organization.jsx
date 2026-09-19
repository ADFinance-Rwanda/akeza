import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge, ConfirmDialog, EmptyState, Field, Modal } from '../components/Common.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api, userMessage } from '../services/api.js'
import { formatDate } from '../utils/format.js'

export default function OrganizationPage() {
  const { orgId, organization, members, projects, me, canAdminOrg, refreshMe, refreshWorkspace, selectOrg, toast, notifyError } = useWorkspace()
  const [creating, setCreating] = useState(!orgId)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [confirmStatus, setConfirmStatus] = useState('')
  const taskCount = projects.reduce((sum, project) => sum + (project.taskCount || 0), 0)

  async function createOrg(e) {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const created = await api.organizations.create({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined
      })
      toast('Organization created successfully')
      setCreating(false)
      setName('')
      setSlug('')
      if (created?.id) selectOrg(String(created.id))
      await refreshMe()
    } catch (err) {
      setError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function applyStatus() {
    setBusy(true)
    try {
      await api.organizations.updateStatus(orgId, confirmStatus)
      toast(confirmStatus === 'SUSPENDED' ? 'Organization suspended' : 'Organization reactivated')
      setConfirmStatus('')
      await refreshWorkspace()
      await refreshMe()
    } catch (err) {
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <div className="page-title">
        <div>
          <h1>Organization</h1>
          <p>Organization settings and membership overview.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => { setError(''); setCreating(true) }}>
          Create organization
        </button>
      </div>

      {!organization ? (
        <EmptyState title="No organization selected" action={<button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>Create organization</button>} />
      ) : (
        <div className="grid two-col">
          <section className="card">
            <h2 className="section-title">{organization.name}</h2>
            <dl className="dl">
              <dt>Name</dt><dd>{organization.name}</dd>
              <dt>Organization code</dt><dd>{organization.slug}</dd>
              <dt>Status</dt><dd><Badge kind="status" value={organization.status} /></dd>
              <dt>Created date</dt><dd>{formatDate(organization.createdAt)}</dd>
              <dt>Members</dt><dd>{members.length}</dd>
              <dt>Projects</dt><dd>{projects.length}</dd>
              <dt>Tasks</dt><dd>{taskCount}</dd>
            </dl>
            {canAdminOrg && (
              <div className="row-actions" style={{ marginTop: 16 }}>
                {organization.status === 'ACTIVE'
                  ? <button type="button" className="btn btn-danger" onClick={() => setConfirmStatus('SUSPENDED')}>Suspend organization</button>
                  : <button type="button" className="btn btn-primary" onClick={() => setConfirmStatus('ACTIVE')}>Reactivate organization</button>}
              </div>
            )}
          </section>
          <section className="card">
            <h2 className="section-title">Quick links</h2>
            <p className="muted">Signed in as {me?.user?.email}</p>
            <div className="row-actions" style={{ marginTop: 12, flexWrap: 'wrap' }}>
              <Link to="/team" className="btn btn-secondary">Team</Link>
              <Link to="/projects" className="btn btn-secondary">Projects</Link>
              {canAdminOrg && <Link to="/audit" className="btn btn-secondary">Audit logs</Link>}
              {canAdminOrg && <Link to="/admin" className="btn btn-secondary">Administration</Link>}
            </div>
          </section>
        </div>
      )}

      {creating && (
        <Modal title="Create organization" onClose={() => setCreating(false)}>
          <form onSubmit={createOrg}>
            <Field id="org-name" label="Organization name">
              <input id="org-name" className="input" value={name} onChange={(e) => setName(e.target.value)} required maxLength={150} />
            </Field>
            <Field id="org-slug" label="Organization code" hint="Lowercase letters, numbers, and hyphens.">
              <input id="org-slug" className="input" value={slug} onChange={(e) => setSlug(e.target.value)} required pattern="^[a-z0-9]+(?:-[a-z0-9]+)*$" />
            </Field>
            <Field id="org-desc" label="Description">
              <textarea id="org-desc" className="textarea" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />
            </Field>
            {error && <p className="err" role="alert">{error}</p>}
            <div className="modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setCreating(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create organization'}</button>
            </div>
          </form>
        </Modal>
      )}

      {confirmStatus && (
        <ConfirmDialog
          title={confirmStatus === 'SUSPENDED' ? 'Suspend organization' : 'Reactivate organization'}
          message={confirmStatus === 'SUSPENDED'
            ? 'Members will be blocked from working in this organization until it is reactivated.'
            : 'Reactivate this organization so members can work again?'}
          confirmLabel={confirmStatus === 'SUSPENDED' ? 'Suspend' : 'Reactivate'}
          danger={confirmStatus === 'SUSPENDED'}
          busy={busy}
          onCancel={() => setConfirmStatus('')}
          onConfirm={applyStatus}
        />
      )}
    </div>
  )
}
