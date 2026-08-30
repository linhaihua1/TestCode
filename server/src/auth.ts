import bcrypt from 'bcryptjs'
import jwt from 'jsonwebtoken'

/** JWT 签名密钥（生产环境务必通过环境变量覆盖） */
const JWT_SECRET = process.env.JWT_SECRET ?? 'api-web-secret-change-me'

/** JWT 载荷 */
export interface JwtPayload {
  userId: string
  username: string
}

/** 密码哈希（bcrypt，cost=10） */
export function hashPassword(password: string): string {
  return bcrypt.hashSync(password, 10)
}

/** 校验明文密码是否匹配哈希 */
export function verifyPassword(password: string, hash: string): boolean {
  return bcrypt.compareSync(password, hash)
}

/** 签发登录 token（7 天有效） */
export function signToken(payload: JwtPayload): string {
  return jwt.sign(payload, JWT_SECRET, { expiresIn: '7d' })
}

/** 验证 token，失败时抛出异常 */
export function verifyToken(token: string): JwtPayload {
  return jwt.verify(token, JWT_SECRET) as JwtPayload
}
