import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { extname, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../..')
const output = execFileSync(
  'git',
  ['ls-files', '--cached', '--others', '--exclude-standard'],
  { cwd: root, encoding: 'utf8' },
)

const excludedParts = ['/node_modules/', '/dist/', '/target/']
const textExtensions = new Set([
  '',
  '.java',
  '.json',
  '.md',
  '.mjs',
  '.properties',
  '.sql',
  '.ts',
  '.vue',
  '.xml',
  '.yaml',
  '.yml',
])
const patterns = [
  ['private key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/u],
  ['GitHub token', /\bgh[pousr]_[A-Za-z0-9]{30,}\b/u],
  ['OpenAI-style key', /\bsk-[A-Za-z0-9_-]{20,}\b/u],
  ['AWS access key', /\bAKIA[0-9A-Z]{16}\b/u],
  ['forbidden default password', /\b(?:cloud_brain_dev|changeit)\b/u],
  ['hard-coded API key marker', /\bhard-coded-api-key\b/u],
]

const findings = []
for (const rawFile of output.split(/\r?\n/u).filter(Boolean)) {
  const file = rawFile.replaceAll('\\', '/')
  if (
    file === 'tests/integration/scan-secrets.mjs' ||
    file === 'tests/integration/verify-scaffold.mjs'
  ) {
    continue
  }
  const normalized = `/${file}`
  if (excludedParts.some((part) => normalized.includes(part))) {
    continue
  }
  if (!textExtensions.has(extname(file).toLowerCase())) {
    continue
  }

  let content
  try {
    content = readFileSync(resolve(root, file), 'utf8')
  } catch {
    continue
  }

  for (const [label, pattern] of patterns) {
    if (pattern.test(content)) {
      findings.push(`${file}: ${label}`)
    }
  }
}

if (findings.length > 0) {
  throw new Error(`疑似敏感信息扫描失败:\n${findings.join('\n')}`)
}

console.log('High-confidence secret patterns not found.')
