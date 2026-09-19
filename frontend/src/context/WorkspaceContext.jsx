import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import keycloak from '../keycloak.js'
import { api, userMessage } from '../services/api.js'
import { roleLabel } from '../utils/format.js'

const WorkspaceContext = createContext(null)

export function WorkspaceProvider({ children }) {
  const [me, setMe] = useState(null)
  const [orgId, setOrgId] = useState(() => localStorage.getItem('orgId') || '')
  const [organization, setOrganization] = useState(null)
  const [projects, setProjects] = useState([])
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [workspaceLoading, setWorkspaceLoading] = useState(false)
  const [bootError, setBootError] = useState('')
  const [toasts, setToasts] = useState([])

  const toast = useCallback((message, type = 'ok') => {
    const id = crypto.randomUUID()
    setToasts((list) => [...list, { id, message, type }])
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 4000)
  }, [])

  const notifyError = useCallback((err) => {
    toast(userMessage(err), 'error')
  }, [toast])

  const membership = useMemo(
    () => me?.organizations?.find((o) => String(o.organizationId) === String(orgId)),
    [me, orgId]
  )
  const role = me?.superAdmin ? 'SUPER_ADMIN' : membership?.role
  const canAdminOrg = Boolean(me?.superAdmin || role === 'ORG_ADMIN')
  const canManageProjects = Boolean(me?.superAdmin || role === 'ORG_ADMIN' || role === 'PROJECT_MANAGER')
  const canWriteTasks = Boolean(me?.superAdmin || role === 'ORG_ADMIN' || role === 'PROJECT_MANAGER' || role === 'MEMBER')

  const refreshMe = useCallback(async () => {
    const data = await api.me()
    setMe(data)
    setOrgId((current) => {
      const orgs = data.organizations || []
      if (!orgs.length) {
        localStorage.removeItem('orgId')
        return ''
      }
      const preferred = orgs.find((o) => String(o.organizationId) === String(current))
      const next = preferred || orgs[0]
      const id = String(next.organizationId)
      localStorage.setItem('orgId', id)
      return id
    })
    return data
  }, [])

  const refreshWorkspace = useCallback(async (id = orgId) => {
    if (!id) {
      setProjects([])
      setMembers([])
      setOrganization(null)
      return
    }
    setWorkspaceLoading(true)
    try {
      const [org, projectList, memberList] = await Promise.all([
        api.organizations.get(id).catch(() => null),
        api.projects.list(id).catch(() => []),
        api.organizations.members(id).catch(() => [])
      ])
      setOrganization(org)
      setProjects(Array.isArray(projectList) ? projectList : [])
      setMembers(Array.isArray(memberList) ? memberList : [])
    } finally {
      setWorkspaceLoading(false)
    }
  }, [orgId])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    refreshMe()
      .catch((err) => { if (!cancelled) setBootError(userMessage(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [refreshMe])

  useEffect(() => {
    if (!orgId) return
    localStorage.setItem('orgId', String(orgId))
    refreshWorkspace(orgId).catch(notifyError)
  }, [orgId, refreshWorkspace, notifyError])

  const selectOrg = (id) => setOrgId(String(id))

  const canEditTask = (task) => {
    if (!canWriteTasks || !task) return false
    if (me?.superAdmin || role === 'ORG_ADMIN' || role === 'PROJECT_MANAGER') return true
    return String(task.createdByUserId) === String(me?.user?.id)
  }

  const canDeleteTask = (task) => {
    if (!task) return false
    return Boolean(me?.superAdmin || role === 'ORG_ADMIN' || role === 'PROJECT_MANAGER')
  }

  const value = {
    me,
    orgId,
    selectOrg,
    organization,
    projects,
    members,
    role,
    roleLabel: roleLabel(role),
    canAdminOrg,
    canManageProjects,
    canWriteTasks,
    canEditTask,
    canDeleteTask,
    loading,
    workspaceLoading,
    bootError,
    refreshMe,
    refreshWorkspace,
    toast,
    notifyError,
    toasts,
    logout: () => keycloak.logout({ redirectUri: window.location.origin })
  }

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}

export function useWorkspace() {
  return useContext(WorkspaceContext)
}
