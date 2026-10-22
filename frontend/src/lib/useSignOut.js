import { useCallback } from 'react'
import { useDispatch } from 'react-redux'
import { logout } from '../features/auth/authSlice.js'
import { activePushSubscription } from './pwaInstall.js'

export default function useSignOut() {
  const dispatch = useDispatch()
  return useCallback(async () => {
    const subscription = await activePushSubscription()
    if (subscription) await subscription.unsubscribe().catch(() => false)
    await dispatch(logout(subscription?.endpoint || null))
    window.localStorage.removeItem('persist:anime-list')
    window.localStorage.removeItem('persist:anime-list-v2')
    window.location.replace('/')
  }, [dispatch])
}
