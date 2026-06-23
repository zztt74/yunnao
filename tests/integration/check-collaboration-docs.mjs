import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../..')
const docsRoot = resolve(root, '智慧云脑诊疗平台_AI协作文档_v2')

const requiredFiles = [
  '00_开始这里.md',
  '01_场景化阅读导航.md',
  'README.md',
  'governance/00_文档优先级与冲突处理.md',
  'governance/01_任务状态与审批.md',
  'governance/02_目录所有权.md',
  'governance/03_变更管理.md',
  'product/10_项目范围.md',
  'product/11_功能需求.md',
  'product/12_业务流程与状态机.md',
  'architecture/20_整体架构.md',
  'architecture/21_模块与依赖边界.md',
  'architecture/22_项目目录结构.md',
  'contracts/30_接口数据与错误契约.md',
  'contracts/31_数据库与Flyway规范.md',
  'contracts/32_AI能力契约规范.md',
  'contracts/33_错误码与时间规范.md',
  'workflow/40_工程开发规范.md',
  'workflow/41_质量测试与完成定义.md',
  'workflow/42_PR与交付规范.md',
]

const missing = requiredFiles.filter(
  (file) => !existsSync(resolve(docsRoot, file)),
)
if (missing.length > 0) {
  throw new Error(`协作文档缺少场景化结构:\n${missing.join('\n')}`)
}

const retiredFiles = [
  'shared/00_AI协作入口与阅读说明.md',
  'shared/01_项目背景_建设目标与范围.md',
  'shared/02_完整功能需求说明.md',
  'shared/03_业务流程_规则与状态机.md',
  'shared/04_总体架构与模块边界.md',
  'shared/05_接口数据与错误契约.md',
  'shared/06_工程规范与AI修改约束.md',
  'shared/07_非功能需求_测试与验收.md',
]
const restored = retiredFiles.filter((file) =>
  existsSync(resolve(docsRoot, file)),
)
if (restored.length > 0) {
  throw new Error(`旧 shared 权威文件不得恢复:\n${restored.join('\n')}`)
}

const roleFiles = [
  'roles/10_整体架构设计AI任务书.md',
  'roles/11_后端开发AI任务书.md',
  'roles/12_前端开发AI任务书.md',
  'roles/13_AI能力集成AI任务书.md',
  'roles/14_联调测试与集成AI任务书.md',
]
for (const roleFile of roleFiles) {
  const content = readFileSync(resolve(docsRoot, roleFile), 'utf8')
  if (!content.includes('实际任务阅读路径')) {
    throw new Error(`角色任务书缺少场景阅读路径: ${roleFile}`)
  }
}

const filesToInspect = [...requiredFiles, ...roleFiles, 'tasks/TASK_TEMPLATE.md']
const oldReference =
  /shared\/(?:00_AI协作入口与阅读说明|01_项目背景_建设目标与范围|02_完整功能需求说明|03_业务流程_规则与状态机|04_总体架构与模块边界|05_接口数据与错误契约|06_工程规范与AI修改约束|07_非功能需求_测试与验收)\.md/u
for (const file of filesToInspect) {
  const content = readFileSync(resolve(docsRoot, file), 'utf8')
  if (oldReference.test(content)) {
    throw new Error(`发现旧 shared 路径引用: ${file}`)
  }
}

const priority = readFileSync(
  resolve(docsRoot, 'governance/00_文档优先级与冲突处理.md'),
  'utf8',
)
for (const required of [
  '## 2. 业务语义',
  '## 3. 接口与数据契约',
  '## 4. 架构、模块和目录边界',
  '## 5. 当前任务执行范围',
  '## 6. 质量、测试和验收',
  '## 8. 必须停止的冲突',
]) {
  if (!priority.includes(required)) {
    throw new Error(`分领域权威来源缺少章节: ${required}`)
  }
}

console.log('Collaboration documentation structure verified.')
