export const notificationSyncInterval = 15 * 60 * 1000
export const notificationSyncRetryInterval = 60 * 1000

export function notificationSyncIsDue(lastSyncedAt, now = Date.now()) {
  return !lastSyncedAt || now - lastSyncedAt >= notificationSyncInterval
}

export function notificationSyncCanAttempt(lastSyncedAt, lastSyncAttemptAt, now = Date.now()) {
  return notificationSyncIsDue(lastSyncedAt, now)
    && (!lastSyncAttemptAt || now - lastSyncAttemptAt >= notificationSyncRetryInterval)
}

export function normalizeNotificationSyncResponse(data, syncedAt = Date.now()) {
  if (Array.isArray(data)) return { items: data, successful: true, syncedAt }
  if (Array.isArray(data?.notifications)) {
    return { items: data.notifications, successful: data.successful === true, syncedAt }
  }
  if (typeof data?.added === 'number') {
    return { items: null, successful: true, syncedAt }
  }
  return { items: null, successful: false, syncedAt }
}
