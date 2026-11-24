const SESSION_KEY = 'forgeflow.session'

export function readSession() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw)
    if (session.expiresAt && session.expiresAt <= Date.now()) {
      clearSession()
      return null
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(loginResponse) {
  const session = {
    accessToken: loginResponse.accessToken,
    tokenType: loginResponse.tokenType || 'Bearer',
    expiresAt: Date.now() + Number(loginResponse.expiresIn || 1800) * 1000,
    role: loginResponse.role,
    displayName: loginResponse.displayName,
  }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY)
}
