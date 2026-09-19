import { useEffect, useMemo, useState } from 'react'
import { NeedOrg } from '../components/Guards.jsx'
import { ConfirmDialog, Drawer, EmptyState, Field, Modal, Pagination, TaskTable } from '../components/Common.jsx'
import TaskForm from '../components/TaskForm.jsx'
import { Icon } from '../components/Icons.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api, userMessage } from '../services/api.js'
import { formatDate, formatDateTime, memberName, projectName } from '../utils/format.js'

const emptyFilters = {
  q: '',
  status: '',
  priority: '',
  projectId: '',
  assigneeUserId: '',
  dueDate: '',
  overdue: false
}

export default function Tasks() {
  const { orgId, projects, members, canWriteTasks, canEditTask, toast, notifyError } = useWorkspace()
  const [filters, setFilters] = useState(emptyFilters)
  const [applied, setApplied] = useState(emptyFilters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 })
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState(null)
  const [selected, setSelected] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  const params = useMemo(() => ({
    page,
    size: 10,
    q: applied.q,
    status: applied.status,
    priority: applied.priority,
    projectId: applied.projectId,
    assigneeUserId: applied.assigneeUserId,
    overdue: applied.overdue,
    dueAfter: applied.dueDate || undefined,
    dueBefore: applied.dueDate || undefined
  }), [page, applied])

  useEffect(() => {
    if (!orgId) return
    let cancelled = false
    setLoading(true)
    api.tasks.list(orgId, params)
      .then((res) => { if (!cancelled) setData(res) })
      .catch(notifyError)
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [orgId, params, notifyError])

  function applyFilters(e) {
    e?.preventDefault()
    setPage(0)
    setApplied({ ...filters })
  }

  function clearFilters() {
    setFilters(emptyFilters)
    setApplied(emptyFilters)
    setPage(0)
  }

  async function createTask(body) {
    setBusy(true)
    setFormError('')
    try {
      await api.tasks.create(body.projectId, orgId, {
        title: body.title,
        description: body.description,
        status: body.status,
        priority: body.priority,
        assigneeUserId: body.assigneeUserId,
        dueDate: body.dueDate
      }, crypto.randomUUID())
      toast('Task created successfully')
      setCreating(false)
      const res = await api.tasks.list(orgId, params)
      setData(res)
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
      await api.tasks.update(editing.projectId, editing.id, orgId, {
        title: body.title,
        description: body.description,
        status: body.status,
        priority: body.priority,
        assigneeUserId: body.assigneeUserId,
        dueDate: body.dueDate,
        version: editing.version
      })
      toast('Task updated successfully')
      setEditing(null)
      setSelected(null)
      const res = await api.tasks.list(orgId, params)
      setData(res)
    } catch (err) {
      setFormError(userMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function confirmDelete() {
    setBusy(true)
    try {
      await api.tasks.delete(deleting.projectId, deleting.id, orgId)
      toast('Task deleted successfully')
      setDeleting(null)
      setSelected(null)
      const res = await api.tasks.list(orgId, params)
      setData(res)
    } catch (err) {
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  const filteredEmpty = !loading && data.content.length === 0 && Object.values(applied).some(Boolean)

  return (
    <NeedOrg>
      <div className="page-title">
        <div>
          <h1>Tasks</h1>
          <p>Manage and track work across your organization.</p>
        </div>
        {canWriteTasks && (
          <button type="button" className="btn btn-primary" onClick={() => { setFormError(''); setCreating(true) }}>
            <Icon name="plus" size={16} /> Create Task
          </button>
        )}
      </div>

      <form className="toolbar" onSubmit={applyFilters}>
        <div className="grow">
          <label className="sr-only" htmlFor="task-search">Search tasks</label>
          <input id="task-search" className="input" placeholder="Search tasks" value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
        </div>
        <Field id="filter-status" label="Status">
          <select id="filter-status" className="select" value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
            <option value="">All</option>
            <option value="TODO">To do</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="DONE">Done</option>
          </select>
        </Field>
        <Field id="filter-priority" label="Priority">
          <select id="filter-priority" className="select" value={filters.priority} onChange={(e) => setFilters({ ...filters, priority: e.target.value })}>
            <option value="">All</option>
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="CRITICAL">Critical</option>
          </select>
        </Field>
        <Field id="filter-project" label="Project">
          <select id="filter-project" className="select" value={filters.projectId} onChange={(e) => setFilters({ ...filters, projectId: e.target.value })}>
            <option value="">All</option>
            {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </Field>
        <Field id="filter-assignee" label="Assignee">
          <select id="filter-assignee" className="select" value={filters.assigneeUserId} onChange={(e) => setFilters({ ...filters, assigneeUserId: e.target.value })}>
            <option value="">All</option>
            {members.map((m) => <option key={m.userId} value={m.userId}>{m.userEmail}</option>)}
          </select>
        </Field>
        <Field id="filter-due" label="Due date">
          <input id="filter-due" className="input" type="date" value={filters.dueDate} onChange={(e) => setFilters({ ...filters, dueDate: e.target.value })} />
        </Field>
        <label className="field" style={{ minWidth: 90 }}>
          <span>Overdue</span>
          <input type="checkbox" checked={filters.overdue} onChange={(e) => setFilters({ ...filters, overdue: e.target.checked })} />
        </label>
        <button type="submit" className="btn btn-secondary">Apply</button>
      </form>

      {loading ? (
        <p className="muted">Loading tasks...</p>
      ) : (
        <TaskTable
          tasks={data.content}
          projects={projects}
          members={members}
          onOpen={setSelected}
          onEdit={setEditing}
          onDelete={setDeleting}
          canEdit={canEditTask}
          empty={
            filteredEmpty
              ? <EmptyState title="No tasks match your filters." action={<button type="button" className="btn btn-secondary" onClick={clearFilters}>Clear filters</button>} />
              : <EmptyState title="No tasks found" action={canWriteTasks ? <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>Create your first task</button> : null} />
          }
        />
      )}
      <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />

      {creating && (
        <Modal title="Create Task" onClose={() => setCreating(false)}>
          <TaskForm
            projects={projects}
            members={members}
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
          <TaskForm
            initial={editing}
            projects={projects}
            members={members}
            showProject={false}
            submitLabel="Save changes"
            busy={busy}
            error={formError}
            onCancel={() => setEditing(null)}
            onSubmit={updateTask}
          />
        </Modal>
      )}

      {selected && (
        <Drawer
          title={selected.title}
          onClose={() => setSelected(null)}
          footer={
            canEditTask(selected) ? (
              <>
                <button type="button" className="btn btn-secondary" onClick={() => { setFormError(''); setEditing(selected) }}>Edit</button>
                <button type="button" className="btn btn-danger" onClick={() => setDeleting(selected)}>Delete</button>
              </>
            ) : null
          }
        >
          <p>{selected.description || 'No description.'}</p>
          <dl className="dl">
            <dt>Status</dt><dd>{selected.status.replace('_', ' ')}</dd>
            <dt>Priority</dt><dd>{selected.priority}</dd>
            <dt>Project</dt><dd>{projectName(projects, selected.projectId)}</dd>
            <dt>Assignee</dt><dd>{memberName(members, selected.assigneeUserId)}</dd>
            <dt>Due date</dt><dd>{formatDate(selected.dueDate)}</dd>
            <dt>Created by</dt><dd>{memberName(members, selected.createdByUserId)}</dd>
            <dt>Created</dt><dd>{formatDateTime(selected.createdAt)}</dd>
            <dt>Updated</dt><dd>{formatDateTime(selected.updatedAt)}</dd>
          </dl>
        </Drawer>
      )}

      {deleting && (
        <ConfirmDialog
          title="Delete Task"
          message="Are you sure you want to delete this task?"
          danger
          busy={busy}
          onCancel={() => setDeleting(null)}
          onConfirm={confirmDelete}
        />
      )}
    </NeedOrg>
  )
}
