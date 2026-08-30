/**
 * 数据库访问模块。
 * 导出全局唯一的 Prisma 客户端单例，供整个后端复用同一数据库连接池。
 */
import { PrismaClient } from '@prisma/client'

// Prisma 客户端单例：进程内共享，避免重复建立连接池
export const prisma = new PrismaClient()
