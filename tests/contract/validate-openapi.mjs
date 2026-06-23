import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const contractDirectory = import.meta.dirname
const cli = resolve(contractDirectory, 'node_modules/@redocly/cli/bin/cli.js')
const specification = resolve(contractDirectory, '../../contracts/openapi.yaml')
const configuration = resolve(contractDirectory, '../../redocly.yaml')

const result = spawnSync(
  process.execPath,
  [cli, 'lint', specification, '--config', configuration],
  { stdio: 'inherit' },
)

process.exit(result.status ?? 1)
