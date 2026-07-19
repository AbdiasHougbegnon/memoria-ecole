import type { AuthResponse } from './types'

const CLE_TOKEN = 'memoria_token'
const CLE_EMAIL = 'memoria_email'
const CLE_UTILISATEUR_ID = 'memoria_utilisateur_id'

export function enregistrerSession(auth: AuthResponse): void {
  localStorage.setItem(CLE_TOKEN, auth.token)
  localStorage.setItem(CLE_EMAIL, auth.email)
  localStorage.setItem(CLE_UTILISATEUR_ID, auth.utilisateurId)
}

export function obtenirToken(): string | null {
  return localStorage.getItem(CLE_TOKEN)
}

export function obtenirEmailConnecte(): string | null {
  return localStorage.getItem(CLE_EMAIL)
}

export function obtenirUtilisateurIdConnecte(): string | null {
  return localStorage.getItem(CLE_UTILISATEUR_ID)
}

export function estConnecte(): boolean {
  return obtenirToken() !== null
}

export function deconnecter(): void {
  localStorage.removeItem(CLE_TOKEN)
  localStorage.removeItem(CLE_EMAIL)
  localStorage.removeItem(CLE_UTILISATEUR_ID)
}
