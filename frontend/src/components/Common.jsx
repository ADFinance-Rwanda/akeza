import { useEffect } from 'react'
import { badgeClass, formatDate, isOverdue, memberName, projectName, roleLabel, statusLabel } from '../utils/format.js'

export function Field({ id, label, error, hint, children }) {
  return (
    <div className="field">
      {label && <label htmlFor={id}>{label}</label>}
      {children}
      {error ? <span className="err" role="alert">{error}</span> : hint ? <span className="hint">{hint}</span> : null}
    </div>
  )
}

export function Badge({ kind, value, children }) {
  return <span className={badgeClass(kind, value)}>{children || statusLabel(value) || value}</span>
}

export function RoleBadge({ role }) {
  return <span className="badge admin">{roleLabel(role)}</span>
}

export function EmptyState({ title, body, action }) {
  return (
    <div className="empty card">
      <h3>{title}</h3>
      {body && <p>{body}</p>}
      {action}
    </div>
  )
}

export function SkeletonPage({ label = 'Loading…' }) {
  return (
    <div>
      <p className="muted" style={{ marginBottom: 12 }}>{label}</p>
      <div className="grid kpis">
        {[1, 2, 3, 4, 5].map((i) => <div key={i} className="skeleton" style={{ height: 96 }} />)}
      </div>
      <div className="skeleton" style={{ height: 240, marginTop: 16 }} />
    </div>
  )
}

export function Modal({ title, onClose, children, wide }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose?.() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div className="modal" style={wide ? { width: 'min(720px, 100%)' } : undefined} role="dialog" aria-modal="true" aria-labelledby="dialog-title" onClick={(e) => e.stopPropagation()}>
        <h2 id="dialog-title">{title}</h2>
        {children}
      </div>
    </div>
  )
}

export function ConfirmDialog({ title, message, confirmLabel = 'Delete', danger, busy, onConfirm, onCancel }) {
  return (
    <Modal title={title} onClose={onCancel}>
      <p>{message}</p>
      <div className="modal-actions">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="button" className={danger ? 'btn btn-danger' : 'btn btn-primary'} onClick={onConfirm} disabled={busy}>
          {busy ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}

export function Drawer({ title, onClose, children, footer }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose?.() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation" style={{ placeItems: 'stretch', justifyContent: 'end', padding: 0 }}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <h2 id="drawer-title" style={{ margin: 0 }}>{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close details">Close</button>
        </div>
        <div className="drawer-body">{children}</div>
        {footer && <div className="modal-actions drawer-foot">{footer}</div>}
      </aside>
    </div>
  )
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages) return null
  const start = Math.max(0, Math.min(page - 2, totalPages - 5))
  const end = Math.min(totalPages - 1, start + 4)
  const pages = []
  for (let i = Math.max(0, end - 4); i <= end; i += 1) pages.push(i)
  return (
    <nav className="pager" aria-label="Pagination">
      <button type="button" className="btn btn-secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</button>
      {pages.map((p) => (
        <button key={p} type="button" className={`btn btn-secondary${p === page ? ' active' : ''}`} aria-current={p === page ? 'page' : undefined} onClick={() => onChange(p)}>
          {p + 1}
        </button>
      ))}
      <button type="button" className="btn btn-secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </nav>
  )
}

export function TaskBadges({ task }) {
  return (
    <span className="row-actions">
      <Badge kind="status" value={task.status}>{task.status?.replace('_', ' ')}</Badge>
      <Badge kind="priority" value={task.priority}>{task.priority}</Badge>
      {isOverdue(task) && <span className="badge archived">Overdue</span>}
    </span>
  )
}

export function TaskTable({ tasks, projects, members, onOpen, onEdit, onDelete, canEdit, canDelete, empty, actions = true }) {
  if (!tasks.length) return empty
  return (
    <div className="table-wrap card table-card">
      <table className="data">
        <thead>
          <tr>
            <th>Task</th>
            <th>Project</th>
            <th>Status</th>
            <th>Priority</th>
            <th>Assignee</th>
            <th>Due date</th>
            <th>Updated</th>
            {actions && <th>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id} className={isOverdue(task) ? 'overdue-row' : undefined}>
              <td>
                <button type="button" className="linkish title" onClick={() => onOpen?.(task)}>{task.title}</button>
              </td>
              <td>{projectName(projects, task.projectId)}</td>
              <td><Badge kind="status" value={task.status}>{task.status?.replace('_', ' ')}</Badge></td>
              <td><Badge kind="priority" value={task.priority}>{task.priority}</Badge></td>
              <td>{memberName(members, task.assigneeUserId)}</td>
              <td>
                {formatDate(task.dueDate)}
                {isOverdue(task) && <div className="muted">Overdue</div>}
              </td>
              <td className="muted">{formatDate(task.updatedAt)}</td>
              {actions && (
                <td>
                  <div className="row-actions">
                    {onOpen && <button type="button" className="btn btn-ghost" onClick={() => onOpen(task)}>View</button>}
                    {canEdit?.(task) && <button type="button" className="btn btn-ghost" onClick={() => onEdit?.(task)}>Edit</button>}
                    {canDelete?.(task) && <button type="button" className="btn btn-ghost" onClick={() => onDelete?.(task)}>Delete</button>}
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
