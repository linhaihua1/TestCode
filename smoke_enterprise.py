# -*- coding: utf-8 -*-
"""
codex-apiweb 企业级功能验证 - 运行时冒烟测试
覆盖：认证鉴权、业务码契约、RBAC 权限、项目 CRUD、输入校验、安全头部、
      健康检查、API 文档、审计、回收站、越权防护
"""
import json
import sys
import requests

BASE = "http://localhost:8080/api/v1"
PASS, FAIL, DETAILS = 0, 0, []

def check(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  PASS  {name}")
    else:
        FAIL += 1
        DETAILS.append(f"{name} :: {detail}")
        print(f"  FAIL  {name}  << {detail}")

def login(username, password):
    r = requests.post(f"{BASE}/auth/login",
                      json={"username": username, "password": password}, timeout=10)
    return r

# ---------- 1. 认证链路 ----------
print("== 1. 认证链路 ==")
r = login("admin", "admin@123")
check("管理员登录成功", r.status_code == 200 and r.json().get("code") == 0, f"{r.status_code} {r.text[:200]}")
token = r.json()["data"]["token"] if r.status_code == 200 else ""
H = {"Authorization": f"Bearer {token}"}

r = login("admin", "wrong-password")
body = r.json() if "json" in r.headers.get("Content-Type", "") else {}
check("错误密码 -> 业务码 1006（非 1002）",
      body.get("code") == 1006 and r.status_code == 200,
      f"{r.status_code} {body}")

r = login("nonexistent_user_x", "whatever123")
body = r.json()
check("不存在用户 -> 同样 1006（防账号枚举）", body.get("code") == 1006, f"{r.status_code} {body}")

r = requests.get(f"{BASE}/projects", timeout=10)
check("无 token 访问受保护接口 -> 1002", r.json().get("code") == 1002, f"{r.status_code} {r.text[:120]}")

r = requests.get(f"{BASE}/projects", headers={"Authorization": "Bearer forged.token.here"}, timeout=10)
check("伪造 token -> 1002", r.json().get("code") == 1002, f"{r.status_code} {r.text[:120]}")

r = requests.get(f"{BASE}/auth/me", headers=H, timeout=10)
me = r.json().get("data") or {}
check("GET /auth/me 返回当前用户信息", r.json().get("code") == 0 and me.get("username") == "admin", r.text[:200])

# ---------- 2. 项目 CRUD ----------
print("== 2. 项目 CRUD ==")
r = requests.post(f"{BASE}/projects", headers=H, json={"name": "企业级验证-冒烟", "description": "smoke"}, timeout=10)
pj = r.json()
check("创建项目成功", r.json().get("code") == 0, r.text[:200])
pid = (pj.get("data") or {}).get("id", "")

r = requests.get(f"{BASE}/projects", headers=H, timeout=10)
check("项目列表包含新项目", r.json().get("code") == 0 and pid in [p.get("id") for p in (r.json().get("data") or [])], r.text[:200])

r = requests.put(f"{BASE}/projects/{pid}", headers=H, json={"name": "企业级验证-冒烟-改", "description": "upd"}, timeout=10)
check("更新项目成功", r.json().get("code") == 0, r.text[:200])

# ---------- 3. 输入校验 ----------
print("== 3. 输入校验 ==")
r = requests.post(f"{BASE}/projects", headers=H, json={"name": ""}, timeout=10)
check("空项目名 -> 400 校验拦截", r.status_code == 400 and r.json().get("code") == 400, f"{r.status_code} {r.text[:150]}")

r = requests.post(f"{BASE}/auth/register", json={"username": "ab", "password": "x"}, timeout=10)
check("注册非法参数 -> 400", r.status_code == 400, f"{r.status_code} {r.text[:150]}")

r = requests.get(f"{BASE}/projects", headers=H, timeout=10)

# ---------- 4. 安全头部与文档端点 ----------
print("== 4. 安全与可观测性 ==")
r = requests.get("http://localhost:8080/actuator/health", timeout=10)
check("健康检查 UP", r.json().get("status") == "UP", r.text[:100])
r = requests.get("http://localhost:8080/actuator/env", timeout=10)
check("actuator env 未暴露（敏感端点关闭）", r.status_code in (401, 403, 404), f"{r.status_code}")
r = requests.get("http://localhost:8080/v3/api-docs", timeout=10)
check("OpenAPI 文档可访问", r.status_code == 200 and len(r.text) > 1000, f"{r.status_code} len={len(r.text)}")
trace_id = r.headers.get("X-Trace-Id", "")
check("响应携带 X-Trace-Id", trace_id != "", f"headers={dict(r.headers)}" if not trace_id else trace_id)

# ---------- 5. 资源归属/越权 ----------
print("== 5. 越权防护 ==")
r = requests.post(f"{BASE}/auth/register", json={"username": "smoke_viewer", "password": "Viewer@123", "email": "v@t.cn"}, timeout=10)
body = r.json()
check("注册普通用户", body.get("code") == 0 or "已存在" in str(body.get("message", "")), r.text[:150])
r = login("smoke_viewer", "Viewer@123")
vbody = r.json()
if vbody.get("code") == 0:
    VH = {"Authorization": f"Bearer {vbody['data']['token']}"}
    r = requests.delete(f"{BASE}/projects/{pid}", headers=VH, timeout=10)
    check("普通用户删除他人项目被拒绝(40x/业务错误)", r.status_code in (403, 401) or r.json().get("code") not in (0,), f"{r.status_code} {r.text[:150]}")
else:
    check("普通用户登录", False, f"{r.status_code} {vbody}")

# ---------- 6. 业务模块可用性（空数据不 500）----------
print("== 6. 核心模块空列表健壮性 ==")
for ep in ["cases", "scenarios", "test-tasks", "reports", "environments", "global-variables", "modules", "users", "audit-logs", "recycle-bin", "apis"]:
    r = requests.get(f"{BASE}/{ep}", headers=H, timeout=10)
    check(f"GET /{ep} 正常返回", r.status_code == 200 and r.json().get("code") == 0, f"{r.status_code} {r.text[:120]}")

# ---------- 7. SQL 注入尝试 ----------
print("== 7. 注入防护 ==")
r = login("' OR '1'='1", "x")
check("登录 SQL 注入被拒绝", r.json().get("code") == 1006, f"{r.status_code} {r.text[:120]}")

# ---------- 清理 ----------
print("== 清理 ==")
r = requests.delete(f"{BASE}/projects/{pid}", headers=H, timeout=10)
check("管理员删除测试项目", r.json().get("code") == 0, r.text[:120])

print(f"\n==== RESULT: PASS={PASS} FAIL={FAIL} ====")
for d in DETAILS:
    print(f"  ! {d}")
sys.exit(0 if FAIL == 0 else 1)
