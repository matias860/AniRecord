const accountId = required('CLOUDFLARE_ACCOUNT_ID')
const apiToken = required('CLOUDFLARE_API_TOKEN')
const project = required('CLOUDFLARE_PAGES_PROJECT')
const domains = required('CLOUDFLARE_PAGES_DOMAINS')
  .split(',')
  .map(value => value.trim().toLowerCase())
  .filter(Boolean)

const endpoint = `https://api.cloudflare.com/client/v4/accounts/${accountId}/pages/projects/${project}/domains`
const existing = await cloudflare(endpoint)
const byName = new Map(existing.result.map(domain => [domain.name.toLowerCase(), domain]))

for (const name of domains) {
  let domain = byName.get(name)
  if (!domain) {
    const created = await cloudflare(endpoint, {
      method: 'POST',
      body: JSON.stringify({ name }),
    })
    domain = created.result
  } else if (['pending', 'error'].includes(domain.status)) {
    const retried = await cloudflare(`${endpoint}/${encodeURIComponent(name)}`, {
      method: 'PATCH',
      body: '{}',
    })
    domain = retried.result
  }
  const errors = [domain.validation_data?.error_message, domain.verification_data?.error_message]
    .filter(Boolean)
    .join('; ')
  console.log(`${name}: ${domain.status}${errors ? ` (${errors})` : ''}`)
}

async function cloudflare(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${apiToken}`,
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })
  const payload = await response.json()
  if (!response.ok || !payload.success) {
    const detail = payload.errors?.map(error => `${error.code}: ${error.message}`).join('; ')
    throw new Error(`Cloudflare API ${response.status}: ${detail || 'request failed'}`)
  }
  return payload
}

function required(name) {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`${name} is required`)
  return value
}
