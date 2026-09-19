import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import keycloak from './keycloak.js'
import BootScreen from './components/BootScreen.jsx'
import { WorkspaceProvider } from './context/WorkspaceContext.jsx'
import Layout from './components/Layout.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Tasks from './pages/Tasks.jsx'
import Projects from './pages/Projects.jsx'
import ProjectDetails from './pages/ProjectDetails.jsx'
import Team from './pages/Team.jsx'
import AuditLogs from './pages/AuditLogs.jsx'
import OrganizationPage from './pages/Organization.jsx'
import Admin from './pages/Admin.jsx'

export default function App() {
  const [ready, setReady] = useState(false)
  const [authError, setAuthError] = useState('')

  useEffect(() => {
    keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then((authenticated) => {
        if (!authenticated) setAuthError('Your session has expired. Please sign in again.')
        setReady(true)
      })
      .catch(() => {
        setAuthError('Could not start sign-in. Is Keycloak running on port 8081?')
        setReady(true)
      })
  }, [])

  if (!ready) {
    return <BootScreen title="Signing in" body="Connecting to Keycloak…" />
  }

  if (authError && !keycloak.token) {
    return (
      <BootScreen
        title="Sign-in required"
        body={authError}
        action={(
          <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
            Try again
          </button>
        )}
      />
    )
  }

  return (
    <WorkspaceProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/tasks" element={<Tasks />} />
            <Route path="/projects" element={<Projects />} />
            <Route path="/projects/:id" element={<ProjectDetails />} />
            <Route path="/team" element={<Team />} />
            <Route path="/audit" element={<AuditLogs />} />
            <Route path="/organization" element={<OrganizationPage />} />
            <Route path="/admin" element={<Admin />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </WorkspaceProvider>
  )
}
