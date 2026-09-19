import { useState } from 'react'
import { NeedOrg } from '../components/Guards.jsx'
import { EmptyState, Field, Modal, RoleBadge } from '../components/Common.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api, userMessage } from '../services/api.js'
import { roleLabel } from '../utils/format.js'

export default function Team() {
  const { orgId, members, canAdminOrg, me, refreshWorkspace, toast, notifyError } = useWorkspace()
  const [adding, setAdding] = useState(false)
  const [changing, setChanging] = useState(null)
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('MEMBER')
  const [newRole, setNewRole] = useState('MEMBER')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function addMember(e) {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await api.organizations.addMember(orgId, { email: email.trim(), role })
      toast('Member added successfully')
      setAdding(false)
      setEmail('')
      await refreshWorkspace()
    } catch (err) {
      setError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function changeRole(e) {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await api.organizations.updateMemberRole(orgId, changing.userId, newRole)
      toast('Role updated successfully')
      setChanging(null)
      await refreshWorkspace()
    } catch (err) {
      setError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <NeedOrg>
      <div className="page-title">
        <div>
          <h1>Team</h1>
          <p>People who can access this organization.</p>
        </div>
        {canAdminOrg && (
          <button type="button" className="btn btn-primary" onClick={() => { setError(''); setAdding(true) }}>
            Add member
          </button>
        )}
      </div>

      {members.length === 0 ? (
        <EmptyState title="No members found" />
      ) : (
        <div className="table-wrap card table-card">
          <table className="data">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
                {canAdminOrg && <th>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.userId}>
                  <td className="title">{m.userEmail?.split('@')[0]}</td>
                  <td>{m.userEmail}</td>
                  <td><RoleBadge role={m.role} /></td>
                  <td>{String(m.userId) === String(me?.user?.id) ? 'You' : 'Member'}</td>
                  {canAdminOrg && (
                    <td>
                      {m.role !== 'ORG_ADMIN' && (
                        <button
                          type="button"
                          className="btn btn-ghost"
                          onClick={() => { setError(''); setNewRole(m.role === 'PROJECT_MANAGER' ? 'MEMBER' : 'PROJECT_MANAGER'); setChanging(m) }}
                        >
                          Change role
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && (
        <Modal title="Add member" onClose={() => setAdding(false)}>
          <form onSubmit={addMember}>
            <Field id="member-email" label="Email">
              <input id="member-email" className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </Field>
            <Field id="member-role" label="Role">
              <select id="member-role" className="select" value={role} onChange={(e) => setRole(e.target.value)}>
                <option value="MEMBER">Member</option>
                <option value="PROJECT_MANAGER">Project Manager</option>
              </select>
            </Field>
            {error && <p className="err" role="alert">{error}</p>}
            <div className="modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setAdding(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Add member'}</button>
            </div>
          </form>
        </Modal>
      )}

      {changing && (
        <Modal title="Change Role" onClose={() => setChanging(null)}>
          <form onSubmit={changeRole}>
            <p className="muted">Current role: {roleLabel(changing.role)}</p>
            <Field id="new-role" label="New role">
              <select id="new-role" className="select" value={newRole} onChange={(e) => setNewRole(e.target.value)}>
                <option value="MEMBER">Member</option>
                <option value="PROJECT_MANAGER">Project Manager</option>
              </select>
            </Field>
            {error && <p className="err" role="alert">{error}</p>}
            <div className="modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setChanging(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Confirm'}</button>
            </div>
          </form>
        </Modal>
      )}
    </NeedOrg>
  )
}
