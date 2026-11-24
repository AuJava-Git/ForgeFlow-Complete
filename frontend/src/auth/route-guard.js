import { canAccessRoute } from './roles.js'

export function routeAccessDecision(session, routeName) {
  if (routeName === 'login') return session?.accessToken ? 'dashboard' : true
  if (routeName === 'forbidden') return session?.accessToken ? true : 'login'
  if (!session?.accessToken) return 'login'
  return canAccessRoute(session.role, routeName) ? true : 'forbidden'
}
