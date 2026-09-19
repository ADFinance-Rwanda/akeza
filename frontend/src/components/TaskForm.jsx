import { useState } from 'react'
import { Field } from './Common.jsx'

const empty = {
  title: '',
  description: '',
  projectId: '',
  assigneeUserId: '',
  status: 'TODO',
  priority: 'MEDIUM',
  dueDate: ''
}

export default function TaskForm({ initial, projects, members, showProject = true, submitLabel, busy, error, onCancel, onSubmit }) {
  const [form, setForm] = useState({
    ...empty,
    ...initial,
    title: initial?.title || '',
    description: initial?.description || '',
    projectId: initial?.projectId ? String(initial.projectId) : '',
    assigneeUserId: initial?.assigneeUserId ? String(initial.assigneeUserId) : '',
    dueDate: initial?.dueDate || ''
  })
  const [errors, setErrors] = useState({})

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function validate() {
    const next = {}
    if (!form.title.trim()) next.title = 'Title is required.'
    if (form.title.length > 200) next.title = 'Title must be 200 characters or fewer.'
    if (form.description.length > 2000) next.description = 'Description must be 2000 characters or fewer.'
    if (showProject && !form.projectId) next.projectId = 'Select a project.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  return (
    <form onSubmit={(e) => {
      e.preventDefault()
      if (busy) return
      if (!validate()) return
      onSubmit({
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        projectId: form.projectId,
        assigneeUserId: form.assigneeUserId ? Number(form.assigneeUserId) : null,
        status: form.status,
        priority: form.priority,
        dueDate: form.dueDate || undefined
      })
    }}>
      <div className="form-grid">
        <Field id="task-title" label="Title" error={errors.title}>
          <input id="task-title" className="input" value={form.title} onChange={set('title')} maxLength={200} required />
        </Field>
        <Field id="task-desc" label="Description" error={errors.description}>
          <textarea id="task-desc" className="textarea" value={form.description} onChange={set('description')} maxLength={2000} />
        </Field>
        {showProject && (
          <Field id="task-project" label="Project" error={errors.projectId}>
            <select id="task-project" className="select" value={form.projectId} onChange={set('projectId')} required>
              <option value="">Select project</option>
              {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </Field>
        )}
        <Field id="task-assignee" label="Assignee">
          <select id="task-assignee" className="select" value={form.assigneeUserId} onChange={set('assigneeUserId')}>
            <option value="">Unassigned</option>
            {members.map((m) => <option key={m.userId} value={m.userId}>{m.userEmail}</option>)}
          </select>
        </Field>
        <div className="grid two-col">
          <Field id="task-status" label="Status">
            <select id="task-status" className="select" value={form.status} onChange={set('status')}>
              <option value="TODO">To do</option>
              <option value="IN_PROGRESS">In progress</option>
              <option value="DONE">Done</option>
            </select>
          </Field>
          <Field id="task-priority" label="Priority">
            <select id="task-priority" className="select" value={form.priority} onChange={set('priority')}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </Field>
        </div>
        <Field id="task-due" label="Due date">
          <input id="task-due" className="input" type="date" value={form.dueDate} onChange={set('dueDate')} />
        </Field>
      </div>
      {error && <p className="err" role="alert" style={{ marginTop: 12 }}>{error}</p>}
      <div className="modal-actions">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
      </div>
    </form>
  )
}
