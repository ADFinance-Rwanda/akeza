import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { Icon } from './Icons.jsx'
import { Modal, RoleBadge } from './Common.jsx'
import { useWorkspace } from '../context/WorkspaceContext.jsx'
import { displayName, initials, roleLabel } from '../utils/format.js'

const TITLES = {
  '/dashboard': { title: 'Dashboard', crumb: 'Overview' },
  '/tasks': { title: 'Tasks', crumb: 'Work' },
  '/projects': { title: 'Projects', crumb: 'Work' },
  '/team': { title: 'Team', crumb: 'Organization' },
  '/audit': { title: 'Audit Logs', crumb: 'Administration' },
  '/organization': { title: 'Organization', crumb: 'Settings' },
  '/admin': { title: 'Administration', crumb: 'Settings' }
}

export default function Layout() {
  const {
    me, orgId, selectOrg, organization, role, canAdminOrg, loading, bootError,
    logout, toasts
  } = useWorkspace()
  const [menuOpen, setMenuOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileModal, setProfileModal] = useState(false)
  const location = useLocation()
  const page = Object.entries(TITLES).find(([path]) => location.pathname.startsWith(path))?.[1]
    || TITLES['/dashboard']

  useEffect(() => {
    setMenuOpen(false)
    setProfileOpen(false)
  }, [location.pathname])

  if (loading) {
    return (
      <div className="boot">
        <div className="spinner" aria-hidden="true" />
        <h1>Task Platform</h1>
        <p>Signing you in…</p>
      </div>
    )
  }

  if (bootError && !me) {
    return (
      <div className="boot">
        <h1>Could not load your workspace</h1>
        <p>{bootError}</p>
      </div>
    )
  }

  const orgs = me?.organizations || []
  const user = me?.user

  return (
    <div className="app-shell">
      <div className={`overlay${menuOpen ? ' show' : ''}`} onClick={() => setMenuOpen(false)} />
      <aside className={`sidebar${menuOpen ? ' open' : ''}`} aria-label="Primary">
        <div className="brand">
          <span className="brand-mark">TP</span>
          Task Platform
        </div>
        <div className="nav-group">Workspace</div>
        <NavLink to="/dashboard" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
          <Icon name="dashboard" /> Dashboard
        </NavLink>
        <NavLink to="/tasks" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
          <Icon name="tasks" /> Tasks
        </NavLink>
        <NavLink to="/projects" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
          <Icon name="projects" /> Projects
        </NavLink>
        {orgId && (
          <NavLink to="/team" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
            <Icon name="team" /> Team
          </NavLink>
        )}
        {canAdminOrg && (
          <>
            <div className="nav-group">Administration</div>
            <NavLink to="/audit" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <Icon name="audit" /> Audit Logs
            </NavLink>
            <NavLink to="/organization" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <Icon name="org" /> Organization
            </NavLink>
            <NavLink to="/admin" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <Icon name="admin" /> Administration
            </NavLink>
          </>
        )}
        {!canAdminOrg && (
          <>
            <div className="nav-group">Account</div>
            <NavLink to="/organization" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <Icon name="org" /> Organization
            </NavLink>
          </>
        )}
      </aside>

      <div className="main">
        <header className="topbar">
          <button type="button" className="icon-btn menu-toggle" onClick={() => setMenuOpen(true)} aria-label="Open navigation">
            <Icon name="menu" />
          </button>
          <div>
            <div className="page-head">
              <h1>{page.title}</h1>
              <div className="crumb">{page.crumb}{organization?.name ? ` / ${organization.name}` : ''}</div>
            </div>
          </div>
          <div className="top-actions">
            {organization && (
              <span className="topbar-status">
                <span className={`status-dot${organization.status === 'SUSPENDED' ? ' danger' : ''}`} />
                {organization.status === 'SUSPENDED' ? 'Suspended' : 'Active'}
              </span>
            )}
            <div className="org-switch">
              <label htmlFor="org-select">Organization</label>
              <select
                id="org-select"
                className="select"
                value={orgId}
                onChange={(e) => selectOrg(e.target.value)}
                disabled={!orgs.length}
              >
                {!orgs.length && <option value="">No organization</option>}
                {orgs.map((org) => (
                  <option key={org.organizationId} value={org.organizationId}>
                    {org.organizationName} · {roleLabel(org.role)}
                  </option>
                ))}
              </select>
            </div>
            <div className="user-menu-wrap">
              <button type="button" className="user-chip" onClick={() => setProfileOpen((v) => !v)} aria-haspopup="menu" aria-expanded={profileOpen}>
                <span className="avatar">{initials(user)}</span>
                <span className="user-meta">
                  <strong>{displayName(user)}</strong>
                  <span>{user?.email}</span>
                </span>
                <RoleBadge role={role} />
              </button>
              {profileOpen && (
                <div className="menu" role="menu">
                  <div style={{ padding: '8px 10px' }}>
                    <strong>{displayName(user)}</strong>
                    <div className="muted">{user?.email}</div>
                    <div style={{ marginTop: 6 }}><RoleBadge role={role} /></div>
                  </div>
                  <button type="button" onClick={() => { setProfileOpen(false); setProfileModal(true) }}>Profile</button>
                  <button type="button" onClick={logout}>Logout</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="content">
          {organization?.status === 'SUSPENDED' && (
            <div className="banner danger">This organization is suspended. Some actions may be unavailable.</div>
          )}
          <Outlet />
        </main>
      </div>

      <div className="toast-wrap" aria-live="polite">
        {toasts.map((t) => <div key={t.id} className={`toast${t.type === 'error' ? ' error' : ''}`}>{t.message}</div>)}
      </div>

      {profileModal && (
        <Modal title="Profile" onClose={() => setProfileModal(false)}>
          <dl className="dl">
            <dt>Name</dt><dd>{displayName(user)}</dd>
            <dt>Email</dt><dd>{user?.email}</dd>
            <dt>Role</dt><dd>{roleLabel(role)}</dd>
            <dt>Organization</dt><dd>{organization?.name || '—'}</dd>
          </dl>
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setProfileModal(false)}>Close</button>
            <button type="button" className="btn btn-primary" onClick={logout}>Logout</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
