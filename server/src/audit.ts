import { Prisma } from '@prisma/client'
import { prisma } from './db.js'

/**
 * 审计日志助手：记录谁在何时对哪个实体做了什么操作（含变更前后快照）。
 * 记录失败不影响主流程（内部吞掉异常）。
 */
export interface AuditUser {
  userId: string
  username?: string
}

export interface AuditInput {
  user?: AuditUser | null
  action: string // create / update / delete / run / rollback / submit / approve / reject ...
  entityType: string // project / environment / api / case / module / task / user / globalVariable ...
  entityId?: string
  before?: unknown
  after?: unknown
  ip?: string
}

export async function recordAudit(input: AuditInput): Promise<void> {
  try {
    await prisma.auditLog.create({
      data: {
        userId: input.user?.userId ?? null,
        action: input.action,
        entityType: input.entityType,
        entityId: input.entityId ?? null,
        before: (input.before === undefined ? undefined : input.before) as Prisma.InputJsonValue,
        after: (input.after === undefined ? undefined : input.after) as Prisma.InputJsonValue,
        ip: input.ip ?? null,
      },
    })
  } catch {
    // 审计失败静默，不影响业务
  }
}
