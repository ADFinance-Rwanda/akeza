import { Link } from 'react-router-dom'
import { useWorkspace } from '../context/WorkspaceContext.jsx'

export function NeedOrg({ children }) {
  const { orgId } = useWorkspace()
  if (orgId) return children
  return (
    <div className="empty card">
      <h3>No organization selected</h3>
      <p>Create or join an organization to start managing work.</p>
      <Link to="/organization" className="btn btn-primary" style={{ marginTop: 8 }}>
        Go to Organization
      </Link>
    </div>
  )
}

export function Forbidden({ message = "You don't have permission to view this page." }) {
  return (
    <div className="empty card">
      <h3>Access restricted</h3>
      <p>{message}</p>
    </div>
  )
}
