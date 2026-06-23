import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../..')
const map = JSON.parse(
  readFileSync(resolve(import.meta.dirname, 'ownership-map.json'), 'utf8'),
)
const taskRoles = new Set([
  '整体架构设计',
  '后端开发',
  '前端开发',
  'AI 能力集成',
  '联调测试与集成',
])

const output = execFileSync(
  'git',
  ['ls-files', '--cached', '--others', '--exclude-standard'],
  { cwd: root, encoding: 'utf8' },
)

const files = [...new Set(output.split(/\r?\n/u).filter(Boolean))].map((file) =>
  file.replaceAll('\\', '/'),
)

function matches(patterns, file) {
  return patterns.some((pattern) => new RegExp(pattern, 'u').test(file))
}

function taskOwner(file) {
  if (
    !file.startsWith('智慧云脑诊疗平台_AI协作文档_v2/tasks/') ||
    file.endsWith('/TASK_TEMPLATE.md')
  ) {
    return null
  }

  const content = readFileSync(resolve(root, file), 'utf8')
  const match = content.match(/^负责人角色：(.+)$/mu)
  if (!match) {
    throw new Error(`任务卡未声明负责人角色: ${file}`)
  }
  const owner = match[1].trim()
  if (!taskRoles.has(owner)) {
    throw new Error(`任务卡负责人角色未登记: ${file}: ${owner}`)
  }
  return `task:${owner}`
}

const governedPrefixes = [
  'backend/',
  'frontend/',
  'contracts/',
  'tests/',
  'postman/',
  'deploy/',
  '.github/workflows/',
  'docs/architecture/',
  'docs/integration/',
  'docs/changes/',
  'docs/superpowers/specs/',
  'docs/superpowers/plans/',
  '智慧云脑诊疗平台_AI协作文档_v2/',
]
const governedFiles = new Set([
  'docker-compose.yml',
  'README.md',
  '.gitignore',
  '.env.example',
  '.dockerignore',
  'redocly.yaml',
])

const errors = []
for (const file of files) {
  if (
    !governedFiles.has(file) &&
    !governedPrefixes.some((prefix) => file.startsWith(prefix))
  ) {
    continue
  }

  const dynamicOwner = taskOwner(file)
  if (dynamicOwner) {
    continue
  }

  const owners = Object.entries(map)
    .filter(([, rule]) => {
      const included = matches(rule.include, file)
      const excluded = matches(rule.exclude, file)
      return included && !excluded
    })
    .map(([owner]) => owner)

  if (owners.length !== 1) {
    errors.push(`${file}: owners=${owners.join(',') || 'none'}`)
  }
}

if (errors.length > 0) {
  throw new Error(`目录所有权映射失败:\n${errors.join('\n')}`)
}

console.log(`Ownership map verified for ${files.length} repository files.`)
