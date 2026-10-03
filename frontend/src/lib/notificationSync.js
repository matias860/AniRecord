export const notificationSyncInterval = 15 * 60 * 1000

export function notificationSyncIsDue(lastSyncedAt, now = Date.now()) {
  return !lastSyncedAt || now - lastSyncedAt >= notificationSyncInterval
}
