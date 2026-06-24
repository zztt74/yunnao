import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../..')
const requiredFiles = [
  'backend/pom.xml',
  'frontend/package.json',
  'contracts/openapi.yaml',
  'docker-compose.yml',
  '.env.example',
  '.github/workflows/ci.yml',
  'frontend/package-lock.json',
  'tests/contract/package-lock.json',
  'tests/integration/ownership-map.json',
  'tests/integration/check-ownership.mjs',
  'tests/integration/scan-secrets.mjs',
  'tests/integration/check-collaboration-docs.mjs',
  '智慧云脑诊疗平台_AI协作文档_v2/roles/10_整体架构设计AI任务书.md',
  '智慧云脑诊疗平台_AI协作文档_v2/00_开始这里.md',
  '智慧云脑诊疗平台_AI协作文档_v2/01_场景化阅读导航.md',
  '智慧云脑诊疗平台_AI协作文档_v2/tasks/STAGE-0-parent.md',
]

const missing = requiredFiles.filter((file) => !existsSync(resolve(root, file)))
if (missing.length > 0) {
  throw new Error(`Stage 0 缺少文件:\n${missing.join('\n')}`)
}

const applicationYaml = readFileSync(
  resolve(root, 'backend/src/main/resources/application.yml'),
  'utf8',
)
if (!applicationYaml.includes('password: ${DB_PASSWORD}')) {
  throw new Error('DB_PASSWORD 必须是无默认值的必填环境变量')
}

const aiYaml = readFileSync(
  resolve(root, 'backend/src/main/resources/application-ai.yml'),
  'utf8',
)
const compose = readFileSync(resolve(root, 'docker-compose.yml'), 'utf8')
if (
  !aiYaml.includes('mode: ${AI_MODE:MOCK}') ||
  !compose.includes('AI_MODE: ${AI_MODE:-MOCK}')
) {
  throw new Error('AI_MODE 在后端配置与 Compose 中必须保持一致')
}

const openapi = readFileSync(resolve(root, 'contracts/openapi.yaml'), 'utf8')
// paths: {} 检查仅对 push 到 main 分支（Stage 0）生效，PR 和功能分支跳过
const isMainPush = process.env.GITHUB_REF === 'refs/heads/main'
if (isMainPush && !openapi.includes('paths: {}')) {
  throw new Error('Stage 0 不得提前定义未经批准的业务接口')
}

const commonSchema = readFileSync(
  resolve(root, 'contracts/schemas/common.yaml'),
  'utf8',
)
const errorCodePattern =
  '^(?:SUCCESS|(?:AUTH|PERMISSION|VALIDATION|USER|PATIENT|DOCTOR|DEPARTMENT|SCHEDULE|APPOINTMENT|TRIAGE|ENCOUNTER|EXAMINATION|LABORATORY|AI|MEDICAL_RECORD|PRESCRIPTION|DEVICE|FILE|AUDIT|SYSTEM)_[A-Z0-9]+(?:_[A-Z0-9]+)*)$'
if (!commonSchema.includes(`pattern: '${errorCodePattern}'`)) {
  throw new Error('OpenAPI 错误码模式与冻结分类不一致')
}
const errorCodeRegex = new RegExp(errorCodePattern, 'u')
for (const valid of ['SUCCESS', 'AUTH_INVALID_CREDENTIALS', 'MEDICAL_RECORD_NOT_CONFIRMED']) {
  if (!errorCodeRegex.test(valid)) {
    throw new Error(`合法错误码被拒绝: ${valid}`)
  }
}
for (const invalid of ['AUTH', 'SUCCESS_FOO', 'UNKNOWN_ERROR']) {
  if (errorCodeRegex.test(invalid)) {
    throw new Error(`非法错误码被接受: ${invalid}`)
  }
}

const frontendTypes = readFileSync(
  resolve(root, 'frontend/src/types/api.ts'),
  'utf8',
)
for (const expected of [
  'code: string',
  'items: T[]',
  'pageSize: number',
  'totalPages: number',
]) {
  if (!frontendTypes.includes(expected)) {
    throw new Error(`前端公共类型未遵守冻结契约: ${expected}`)
  }
}
if (frontendTypes.includes('timestamp:')) {
  throw new Error('统一响应不得增加未冻结的 timestamp 字段')
}

const forbiddenSecrets = ['cloud_brain_dev', 'changeit', 'hard-coded-api-key']
const securityInputs = [applicationYaml, aiYaml, compose]
for (const forbidden of forbiddenSecrets) {
  if (securityInputs.some((content) => content.includes(forbidden))) {
    throw new Error(`发现禁止的源码默认密钥: ${forbidden}`)
  }
}

const envExample = readFileSync(resolve(root, '.env.example'), 'utf8')
for (const variable of [
  'DB_URL',
  'DB_USERNAME',
  'DB_PASSWORD',
  'JWT_SECRET',
  'INITIAL_ADMIN_USERNAME',
  'INITIAL_ADMIN_PASSWORD',
  'CORS_ALLOWED_ORIGINS',
  'AI_MODE',
  'AI_API_URL',
  'AI_API_KEY',
]) {
  if (!new RegExp(`^${variable}=`, 'mu').test(envExample)) {
    throw new Error(`.env.example 缺少必需变量: ${variable}`)
  }
}

console.log('Stage 0 scaffold invariants verified.')
