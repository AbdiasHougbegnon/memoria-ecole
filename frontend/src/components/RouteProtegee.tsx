import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { estConnecte } from '../auth'

export function RouteProtegee({ children }: { children: ReactNode }) {
  if (!estConnecte()) {
    return <Navigate to="/connexion" replace />
  }
  return children
}
