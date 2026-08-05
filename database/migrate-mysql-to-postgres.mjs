import fs from 'node:fs'
import mysql from 'mysql2/promise'
import { neon } from '@neondatabase/serverless'

const propertiesPath = process.env.MYSQL_PROPERTIES
const postgresUrl = process.env.POSTGRES_URL

if (!propertiesPath || !postgresUrl) {
  throw new Error('Faltan MYSQL_PROPERTIES o POSTGRES_URL.')
}

const properties = Object.fromEntries(
  fs.readFileSync(propertiesPath, 'utf8')
    .split(/\r?\n/)
    .filter((line) => line && !line.trimStart().startsWith('#'))
    .map((line) => {
      const separator = line.indexOf('=')
      return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()]
    }),
)

const sourceUrl = new URL(properties['spring.datasource.url'].replace('jdbc:', ''))
const source = await mysql.createConnection({
  host: sourceUrl.hostname,
  port: Number(sourceUrl.port || 3306),
  database: sourceUrl.pathname.slice(1),
  user: properties['spring.datasource.username'],
  password: properties['spring.datasource.password'],
  dateStrings: true,
})

const target = neon(postgresUrl, { fetchOptions: { signal: AbortSignal.timeout(30000) } })

const schemaPath = new URL('../backend/src/main/resources/db/migration/V1__create_anime_list_schema.sql', import.meta.url)
const schemaStatements = fs.readFileSync(schemaPath, 'utf8')
  .split(';')
  .map((statement) => statement.trim())
  .filter(Boolean)

await target.query('DROP SCHEMA public CASCADE')
await target.query('CREATE SCHEMA public')
for (const [index, statement] of schemaStatements.entries()) {
  await target.query(statement)
  console.log(`Esquema: ${index + 1}/${schemaStatements.length}`)
}

const tables = [
  ['app_user', ['id', 'display_name', 'bio', 'avatar_url', 'banner_url', 'background_color', 'surface_color', 'accent_color', 'dots_enabled', 'created_at', 'updated_at']],
  ['media', ['id', 'anilist_id', 'media_type', 'title', 'cover_image', 'banner_image', 'synopsis', 'total_units', 'release_start_date', 'release_end_date', 'release_status', 'release_season', 'release_year', 'last_synced_at', 'created_at', 'updated_at']],
  ['library_entries', ['id', 'media_id', 'progress', 'personal_score', 'rewatch_count', 'tracking_status', 'started_at', 'completed_at', 'created_at', 'updated_at']],
  ['activity_history', ['id', 'media_id', 'activity_type', 'previous_progress', 'new_progress', 'previous_status', 'new_status', 'previous_score', 'new_score', 'previous_rewatch_count', 'new_rewatch_count', 'created_at']],
  ['anilist_connection', ['id', 'access_token', 'token_expires_at', 'username', 'connected_at']],
  ['app_notification', ['id', 'anilist_notification_id', 'notification_type', 'anilist_media_id', 'title', 'cover_image', 'message', 'episode', 'is_read', 'occurred_at', 'created_at']],
]

try {
  await target.query('TRUNCATE app_notification, activity_history, library_entries, media, anilist_connection, app_user RESTART IDENTITY CASCADE')

  for (const [table, columns] of tables) {
    const [rows] = await source.query(`SELECT ${columns.map((column) => `\`${column}\``).join(', ')} FROM \`${table}\``)
    for (let offset = 0; offset < rows.length; offset += 100) {
      const batch = rows.slice(offset, offset + 100)
      const values = batch.flatMap((row) => columns.map((column) => row[column]))
      const placeholders = batch.map((_, rowIndex) => {
        const start = rowIndex * columns.length
        return `(${columns.map((__, columnIndex) => `$${start + columnIndex + 1}`).join(', ')})`
      }).join(', ')
      await target.query(
        `INSERT INTO ${table} (${columns.join(', ')}) VALUES ${placeholders}`,
        values,
      )
    }
    console.log(`${table}: ${rows.length} filas migradas`)
  }

  await target.query("SELECT setval(pg_get_serial_sequence('media','id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM media")
  await target.query("SELECT setval(pg_get_serial_sequence('library_entries','id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM library_entries")
  await target.query("SELECT setval(pg_get_serial_sequence('activity_history','id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM activity_history")
  await target.query("SELECT setval(pg_get_serial_sequence('app_notification','id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM app_notification")
} catch (error) {
  throw error
} finally {
  await source.end()
}
