import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { NeedOrg } from '../components/Guards.jsx'
import { Badge, EmptyState, Field, Modal } from '../components/Common.jsx'
import { Icon } from '../components/Icons.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api, userMessage } from '../services/api.js'
import { formatDate } from '../utils/format.js'

export default function Projects() {
  const { orgId, projects, canManageProjects, refreshWorkspace, toast } = useWorkspace()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function createProject(e) {
    e.preventDefault()
    if (busy) return
    if (!name.trim()) {
      setError('Project name is required.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api.projects.create(orgId, { name: name.trim(), description: description.trim() || undefined })
      toast('Project created successfully')
      setCreating(false)
      setName('')
      setDescription('')
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
          <h1>Projects</h1>
          <p>Group work into projects your team can track.</p>
        </div>
        {canManageProjects && (
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
            <Icon name="plus" size={16} /> New Project
          </button>
        )}
      </div>

      {projects.length === 0 ? (
        <EmptyState
          title="No projects yet"
          action={canManageProjects ? <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>Create project</button> : null}
        />
      ) : (
        <div className="grid project-cards">
          {projects.map((project) => (
            <button
              key={project.id}
              type="button"
              className="card project-card"
              onClick={() => navigate(`/projects/${project.id}`)}
            >
              <div className="project-card-head">
                <h3>{project.name}</h3>
                <Badge kind="status" value={project.status} />
              </div>
              <p className="muted">{project.description || 'No description'}</p>
              <div className="stats-inline">
                <span><strong>{project.taskCount ?? 0}</strong> tasks</span>
                <span>Created {formatDate(project.createdAt)}</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {creating && (
        <Modal title="New Project" onClose={() => setCreating(false)}>
          <form onSubmit={createProject}>
            <Field id="project-name" label="Project name" error={!name.trim() && error ? error : undefined}>
              <input id="project-name" className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={150} required />
            </Field>
            <Field id="project-desc" label="Description">
              <textarea id="project-desc" className="textarea" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />
            </Field>
            {error && name.trim() && <p className="err" role="alert">{error}</p>}
            <div className="modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setCreating(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Create project'}</button>
            </div>
          </form>
        </Modal>
      )}
    </NeedOrg>
  )
}
