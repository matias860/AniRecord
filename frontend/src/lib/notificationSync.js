export const notificationSyncInterval = 15 * 60 * 1000

export function notificationSyncIsDue(lastSyncedAt, now = Date.now()) {
  return !lastSyncedAt || now - lastSyncedAt >= notificationSyncInterval
}

export function normalizeNotificationSyncResponse(data, syncedAt = Date.now()) {
  return Array.isArray(data)
    ? { items: data, successful: true, syncedAt }
    : { items: data?.notifications ?? [], successful: data?.successful === true, syncedAt }
}
