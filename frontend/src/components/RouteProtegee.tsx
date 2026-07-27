import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { estAdminConnecte, estConnecte, obtenirModuleConnecte } from '../auth'
import type { ModuleMemoria } from '../types'
import { Layout } from './Layout'

export function RouteProtegee({
  children,
  module,
  adminRequis,
}: {
  children: ReactNode
  module?: ModuleMemoria
  adminRequis?: boolean
}) {
  if (!estConnecte()) {
    return <Navigate to="/choix-module" replace />
  }
  // Garde de commodite UX, pas une frontiere de securite : la veritable
  // restriction est cote backend (SecurityConfig, hasAuthority MODULE_*/ROLE_ADMIN).
  if (module && obtenirModuleConnecte() !== module) {
    return <Navigate to="/" replace />
  }
  if (adminRequis && !estAdminConnecte()) {
    return <Navigate to="/" replace />
  }
  return <Layout>{children}</Layout>
}
