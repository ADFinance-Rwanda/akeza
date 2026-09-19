import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { NeedOrg } from '../components/Guards.jsx'
import { Badge, ConfirmDialog, Drawer, EmptyState, Field, Modal, Pagination, TaskTable } from '../components/Common.jsx'
import TaskForm from '../components/TaskForm.jsx'
import { Icon } from '../components/Icons.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api, userMessage } from '../services/api.js'
import { formatDate, memberName } from '../utils/format.js'

export default function ProjectDetails() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { orgId, projects, members, canManageProjects, canWriteTasks, canEditTask, canDeleteTask, refreshWorkspace, toast, notifyError } = useWorkspace()
  const [project, setProject] = useState(null)
  const [tasks, setTasks] = useState({ content: [], totalPages: 0, totalElements: 0 })
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState(null)
  const [selected, setSelected] = useState(null)
  const [deletingTask, setDeletingTask] = useState(null)
  const [deletingProject, setDeletingProject] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [editingProject, setEditingProject] = useState(false)

  async function load() {
    if (!orgId) return
    setLoading(true)
    try {
      const [p, t] = await Promise.all([
        api.projects.get(id, orgId),
        api.tasks.byProject(id, orgId, { page, size: 10 })
      ])
      setProject(p)
      setName(p.name)
      setDescription(p.description || '')
      setTasks(t)
    } catch (err) {
      notifyError(err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [orgId, id, page])

  async function saveProject(e) {
    e.preventDefault()
    setBusy(true)
    setFormError('')
    try {
      const updated = await api.projects.update(id, orgId, { name, description, status: project.status })
      setProject(updated)
      setEditingProject(false)
      toast('Project updated successfully')
      await refreshWorkspace()
    } catch (err) {
      setFormError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function setStatus(status) {
    setBusy(true)
    try {
      const updated = await api.projects.update(id, orgId, { status })
      setProject(updated)
      toast(status === 'ARCHIVED' ? 'Project archived' : 'Project restored')
      await refreshWorkspace()
    } catch (err) {
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  async function createTask(body) {
    setBusy(true)
    setFormError('')
    try {
      await api.tasks.create(id, orgId, {
        title: body.title,
        description: body.description,
        status: body.status,
        priority: body.priority,
        assigneeUserId: body.assigneeUserId,
        dueDate: body.dueDate
      }, crypto.randomUUID())
      toast('Task created successfully')
      setCreating(false)
      await load()
    } catch (err) {
      setFormError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function updateTask(body) {
    setBusy(true)
    setFormError('')
    try {
      await api.tasks.update(editing.projectId, editing.id, orgId, { ...body, version: editing.version })
      toast('Task updated successfully')
      setEditing(null)
      await load()
    } catch (err) {
      setFormError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function confirmDeleteTask() {
    setBusy(true)
    try {
      await api.tasks.delete(deletingTask.projectId, deletingTask.id, orgId)
      toast('Task deleted successfully')
      setDeletingTask(null)
      await load()
    } catch (err) {
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  async function confirmDeleteProject() {
    setBusy(true)
    try {
      await api.projects.delete(id, orgId)
      toast('Project deleted successfully')
      await refreshWorkspace()
      navigate('/projects')
    } catch (err) {
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  const fromList = projects.find((p) => String(p.id) === String(id))
  const view = project || fromList

  return (
    <NeedOrg>
      <p className="crumb" style={{ marginBottom: 12 }}>
        <Link to="/projects">Projects</Link> / {view?.name || 'Details'}
      </p>
      {loading && !view ? (
        <p className="muted">Loading project...</p>
      ) : !view ? (
        <EmptyState title="The requested item could not be found." />
      ) : (
        <>
          <div className="page-title">
            <div>
              <h1>{view.name}</h1>
              <p>{view.description || 'No description'}</p>
            </div>
            <div className="row-actions">
              <Badge kind="status" value={view.status} />
              {canWriteTasks && (
                <button type="button" className="btn btn-primary" onClick={() => { setFormError(''); setCreating(true) }}>
                  <Icon name="plus" size={16} /> Create Task
                </button>
              )}
            </div>
          </div>

          <section className="card" style={{ marginBottom: 16 }}>
            <h2 className="section-title">Project information</h2>
            <dl className="dl">
              <dt>Status</dt><dd>{view.status}</dd>
              <dt>Created</dt><dd>{formatDate(view.createdAt)}</dd>
              <dt>Updated</dt><dd>{formatDate(view.updatedAt)}</dd>
              <dt>Tasks</dt><dd>{tasks.totalElements}</dd>
            </dl>
            {canManageProjects && (
              <div className="row-actions" style={{ marginTop: 12 }}>
                <button type="button" className="btn btn-secondary" onClick={() => setEditingProject(true)}>Edit</button>
                {view.status === 'ACTIVE'
                  ? <button type="button" className="btn btn-secondary" onClick={() => setStatus('ARCHIVED')} disabled={busy}>Archive</button>
                  : <button type="button" className="btn btn-secondary" onClick={() => setStatus('ACTIVE')} disabled={busy}>Restore</button>}
                <button type="button" className="btn btn-danger" onClick={() => setDeletingProject(true)}>Delete</button>
              </div>
            )}
          </section>

          <section>
            <h2 className="section-title">Tasks belonging to project</h2>
            <TaskTable
              tasks={tasks.content}
              projects={[view]}
              members={members}
              onOpen={setSelected}
              onEdit={setEditing}
              onDelete={setDeletingTask}
              canEdit={canEditTask}
              canDelete={canDeleteTask}
              empty={<EmptyState title="No tasks found" action={canWriteTasks ? <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>Create your first task</button> : null} />}
            />
            <Pagination page={page} totalPages={tasks.totalPages} onChange={setPage} />
          </section>
        </>
      )}

      {creating && (
        <Modal title="Create Task" onClose={() => setCreating(false)}>
          <TaskForm
            initial={{ projectId: id }}
            projects={[view].filter(Boolean)}
            members={members}
            showProject={false}
            submitLabel="Create Task"
            busy={busy}
            error={formError}
            onCancel={() => setCreating(false)}
            onSubmit={createTask}
          />
        </Modal>
      )}
      {editing && (
        <Modal title="Edit Task" onClose={() => setEditing(null)}>
          <TaskForm initial={editing} projects={projects} members={members} showProject={false} submitLabel="Save changes" busy={busy} error={formError} onCancel={() => setEditing(null)} onSubmit={updateTask} />
        </Modal>
      )}
      {selected && (
        <Drawer
          title={selected.title}
          onClose={() => setSelected(null)}
          footer={
            canEditTask(selected) || canDeleteTask(selected) ? (
              <>
                {canEditTask(selected) && <button type="button" className="btn btn-secondary" onClick={() => { setFormError(''); setEditing(selected) }}>Edit</button>}
                {canDeleteTask(selected) && <button type="button" className="btn btn-danger" onClick={() => setDeletingTask(selected)}>Delete</button>}
              </>
            ) : null
          }
        >
          <p>{selected.description || 'No description.'}</p>
          <dl className="dl">
            <dt>Status</dt><dd>{selected.status.replace('_', ' ')}</dd>
            <dt>Priority</dt><dd>{selected.priority}</dd>
            <dt>Assignee</dt><dd>{memberName(members, selected.assigneeUserId)}</dd>
            <dt>Due date</dt><dd>{formatDate(selected.dueDate)}</dd>
          </dl>
        </Drawer>
      )}
      {editingProject && (
        <Modal title="Edit project" onClose={() => setEditingProject(false)}>
          <form onSubmit={saveProject}>
            <Field id="edit-name" label="Project name">
              <input id="edit-name" className="input" value={name} onChange={(e) => setName(e.target.value)} required />
            </Field>
            <Field id="edit-desc" label="Description">
              <textarea id="edit-desc" className="textarea" value={description} onChange={(e) => setDescription(e.target.value)} />
            </Field>
            {formError && <p className="err" role="alert">{formError}</p>}
            <div className="modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setEditingProject(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={busy}>Save</button>
            </div>
          </form>
        </Modal>
      )}
      {deletingTask && (
        <ConfirmDialog title="Delete Task" message="Are you sure you want to delete this task?" danger busy={busy} onCancel={() => setDeletingTask(null)} onConfirm={confirmDeleteTask} />
      )}
      {deletingProject && (
        <ConfirmDialog title="Delete Project" message="Are you sure you want to delete this project?" danger busy={busy} onCancel={() => setDeletingProject(false)} onConfirm={confirmDeleteProject} />
      )}
    </NeedOrg>
  )
}
