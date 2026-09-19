import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer,
  Tooltip, XAxis, YAxis, Bar, BarChart
} from 'recharts'
import { NeedOrg } from '../components/Guards.jsx'
import { Badge, EmptyState, SkeletonPage } from '../components/Common.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { api } from '../services/api.js'
import { formatDate, memberName, projectName } from '../utils/format.js'

const STATUS_ORDER = ['TODO', 'IN_PROGRESS', 'DONE']
const PRIORITY_ORDER = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const STATUS_COLORS = { TODO: '#2563eb', IN_PROGRESS: '#d97706', DONE: '#059669' }
const PRIORITY_COLORS = { LOW: '#94a3b8', MEDIUM: '#2563eb', HIGH: '#d97706', CRITICAL: '#7c3aed' }

export default function Dashboard() {
  const { orgId, organization, projects, members, notifyError, workspaceLoading } = useWorkspace()
  const [data, setData] = useState(null)
  const [recent, setRecent] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!orgId) return
    let cancelled = false
    setLoading(true)
    Promise.all([
      api.dashboard(orgId),
      api.tasks.list(orgId, { page: 0, size: 8 })
    ])
      .then(([dash, tasks]) => {
        if (cancelled) return
        setData(dash)
        setRecent(tasks.content || [])
      })
      .catch(notifyError)
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [orgId, notifyError])

  return (
    <NeedOrg>
      <div className="page-title">
        <div>
          <h1>Dashboard</h1>
          <p>{organization?.name || 'Organization'} — Overview of your organization's work</p>
        </div>
      </div>
      {(loading || workspaceLoading) && !data ? (
        <SkeletonPage label="Loading dashboard..." />
      ) : !data ? (
        <EmptyState title="No analytics yet" body="Create a project and tasks to see activity here." />
      ) : (
        <>
          <div className="grid kpis">
            <Kpi label="Total tasks" value={data.totalTasks} />
            <Kpi label="Pending" value={data.pendingTasks} />
            <Kpi label="Completed" value={data.completedTasks} tone="success" />
            <Kpi label="Overdue" value={data.overdueTasks} tone="overdue" />
            <Kpi label="Completion rate" value={`${data.completionRate ?? 0}%`} />
          </div>

          <div className="grid two-col charts" style={{ marginTop: 16 }}>
            <section className="card">
              <h2 className="section-title">Task status</h2>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={240}>
                  <PieChart>
                    <Pie data={toChart(data.tasksByStatus, STATUS_ORDER)} dataKey="value" nameKey="name" innerRadius={58} outerRadius={84} paddingAngle={2}>
                      {STATUS_ORDER.map((key) => <Cell key={key} fill={STATUS_COLORS[key]} />)}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </section>
            <section className="card">
              <h2 className="section-title">Task priorities</h2>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={toChart(data.tasksByPriority, PRIORITY_ORDER)} layout="vertical" margin={{ left: 16, right: 16 }}>
                    <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                    <XAxis type="number" allowDecimals={false} />
                    <YAxis type="category" dataKey="name" width={80} />
                    <Tooltip />
                    <Bar dataKey="value" radius={[0, 8, 8, 0]}>
                      {PRIORITY_ORDER.map((key) => <Cell key={key} fill={PRIORITY_COLORS[key]} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </section>
          </div>

          <section className="card" style={{ marginTop: 16 }}>
            <h2 className="section-title">Activity / trends</h2>
            <p className="muted" style={{ marginTop: -8 }}>Created and completed tasks over the last 14 days</p>
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={mergeTrends(data.createdTrend, data.completedTrend)} margin={{ left: 8, right: 16, top: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="label" />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="created" name="Created" stroke="#4f46e5" strokeWidth={2} dot={{ r: 3 }} />
                  <Line type="monotone" dataKey="completed" name="Completed" stroke="#059669" strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section className="card" style={{ marginTop: 16, padding: 0 }}>
            <div className="page-title" style={{ padding: '16px 18px 0', marginBottom: 0 }}>
              <h2 className="section-title">Recent tasks</h2>
              <Link to="/tasks" className="btn btn-ghost" style={{ textDecoration: 'none' }}>View all tasks →</Link>
            </div>
            {recent.length === 0 ? (
              <EmptyState title="No tasks found" action={<Link to="/tasks" className="btn btn-primary" style={{ textDecoration: 'none' }}>Create your first task</Link>} />
            ) : (
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>Task</th>
                      <th>Project</th>
                      <th>Status</th>
                      <th>Priority</th>
                      <th>Assignee</th>
                      <th>Due date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recent.map((task) => (
                      <tr key={task.id}>
                        <td className="title">{task.title}</td>
                        <td>{projectName(projects, task.projectId)}</td>
                        <td><Badge kind="status" value={task.status}>{task.status?.replace('_', ' ')}</Badge></td>
                        <td><Badge kind="priority" value={task.priority}>{task.priority}</Badge></td>
                        <td>{memberName(members, task.assigneeUserId)}</td>
                        <td>{formatDate(task.dueDate)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </NeedOrg>
  )
}

function Kpi({ label, value, tone }) {
  return (
    <article className={`card kpi ${tone || ''}`}>
      <div className="label">{label}</div>
      <div className="value">{value}</div>
    </article>
  )
}

function toChart(map, order) {
  return order.map((name) => ({ name: name.replace('_', ' '), value: Number(map?.[name] || 0) }))
}

function mergeTrends(created = [], completed = []) {
  const map = new Map()
  created.forEach((p) => map.set(p.date, { date: p.date, created: p.count, completed: 0 }))
  completed.forEach((p) => {
    const cur = map.get(p.date) || { date: p.date, created: 0, completed: 0 }
    cur.completed = p.count
    map.set(p.date, cur)
  })
  return [...map.values()]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((p) => ({
      ...p,
      label: new Date(`${p.date}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
    }))
}
