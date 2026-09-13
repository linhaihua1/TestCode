# -*- coding: utf-8 -*-
"""codex-apiweb 全系统功能测试脚本（第二轮：修正传参 + 增加异步轮询/权限/隔离校验）。

覆盖：认证、项目、环境、全局变量、模块、接口定义、用例、场景、
测试任务（接口自动化）、报告、UI 自动化、性能测试、回收站、
审计日志、执行机、CI/CD 流水线与 Jenkins 集成、权限与数据隔离。

用法：python tools_functional_test.py
"""
import json
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080/api/v1"
TOKEN = ""

results = []   # (模块, 用例, 是否通过, 说明)
_ctx = {}      # 跨用例共享的 ID


# ---------------------------------------------------------------- 基础工具
def call(method, path, body=None, params=None, auth=True):
    """发起一次 HTTP 请求，返回 (http_code, 解析后的 data, 原始文本)。"""
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if auth and TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            raw = resp.read().decode("utf-8", "replace")
            code = resp.getcode()
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        code = e.code
    except Exception as e:
        return 0, None, "EXC: %s" % e
    try:
        parsed = json.loads(raw) if raw.strip().startswith(("{", "[")) else raw
    except Exception:
        parsed = raw
    return code, parsed, raw


def biz_code(parsed):
    """取业务码：包装结构返回 code，裸结构视为 0。"""
    if isinstance(parsed, dict) and "code" in parsed:
        return parsed.get("code")
    return 0


def data_of(parsed):
    if isinstance(parsed, dict) and "data" in parsed:
        return parsed["data"]
    return parsed


def record(module, name, passed, msg=""):
    results.append((module, name, passed, msg if not passed else ""))
    print("[%s] %-6s %s%s" % ("PASS" if passed else "FAIL", module, name,
                              "" if passed else "  -> " + str(msg)[:400]))
    return passed


def check(module, name, code, parsed, raw,
          ok_codes=(200,), expect_biz_ok=True, expect_biz_fail=False):
    """记录用例结果。

    expect_biz_ok=True  -> 要求业务码为 0
    expect_biz_fail=True-> 要求业务码非 0（业务校验生效），HTTP 通常仍是 200
    """
    passed = code in ok_codes
    msg = ""
    if passed:
        bc = biz_code(parsed)
        if expect_biz_fail:
            passed = bc != 0
            msg = "期望业务失败，实际 code=%s msg=%s" % (bc, parsed.get("message") if isinstance(parsed, dict) else "")
        elif expect_biz_ok:
            passed = bc == 0
            msg = parsed.get("message") if isinstance(parsed, dict) else raw[:200]
    else:
        msg = "HTTP %s: %s" % (code, raw[:300])
    return record(module, name, passed, msg)


def wait_until(fn, predicate, timeout=180, interval=2, label=""):
    """轮询直到 predicate(data) 为真，返回最终 data；超时返回最后一次结果。"""
    t0 = time.time()
    last = None
    while time.time() - t0 < timeout:
        last = fn()
        try:
            if predicate(last):
                break
        except Exception:
            pass
        time.sleep(interval)
    if label:
        print("      ... %s 等待 %.1fs" % (label, time.time() - t0))
    return last


# ---------------------------------------------------------------- A 认证与权限
def t_auth():
    global TOKEN
    c, p, r = call("POST", "/auth/login", {"username": "admin", "password": "admin@123"}, auth=False)
    if not check("AUTH", "管理员登录", c, p, r):
        return False
    d = data_of(p)
    TOKEN = d.get("token") if isinstance(d, dict) else None
    _ctx["token"] = TOKEN

    c, p, r = call("GET", "/auth/me")
    check("AUTH", "查询当前用户", c, p, r)

    # 错误密码：HTTP 200 但业务码非 0
    c, p, r = call("POST", "/auth/login", {"username": "admin", "password": "wrong-pwd"}, auth=False)
    check("AUTH", "错误密码被拒绝", c, p, r, ok_codes=(200, 401, 400), expect_biz_fail=True)

    # 无 token 访问受保护资源
    saved, TOKEN = TOKEN, ""
    c, p, r = call("GET", "/projects")
    record("AUTH", "无 Token 访问被拒绝", c in (401, 403), "实际 HTTP %s" % c)
    TOKEN = saved

    # 无效 token
    saved, TOKEN = TOKEN, "invalid.jwt.token"
    c, p, r = call("GET", "/projects")
    record("AUTH", "无效 Token 被拒绝", c in (401, 403), "实际 HTTP %s" % c)
    TOKEN = saved

    # 创建普通成员用于权限测试
    ts = str(int(time.time()))
    c, p, r = call("POST", "/auth/register", {"username": "member_" + ts, "password": "test@123"})
    check("AUTH", "注册普通成员", c, p, r, ok_codes=(200, 400, 500))
    if c == 200:
        _ctx["memberName"] = "member_" + ts
        _ctx["memberPwd"] = "test@123"

    # 弱密码应被拒绝
    c, p, r = call("POST", "/auth/register", {"username": "weak_" + ts, "password": "123"})
    check("AUTH", "弱密码被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)

    # 重复用户名应被拒绝
    c, p, r = call("POST", "/auth/register", {"username": "member_" + ts, "password": "test@123"})
    check("AUTH", "重复用户名被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)
    return True


def t_permission():
    """普通成员不应能访问管理员接口（Jenkins 服务器 / 用户管理）。"""
    global TOKEN
    if not _ctx.get("memberName"):
        print("[SKIP] PERM   权限测试（无成员账号）")
        return True
    saved = TOKEN
    c, p, r = call("POST", "/auth/login",
                   {"username": _ctx["memberName"], "password": _ctx["memberPwd"]}, auth=False)
    if c != 200 or biz_code(p) != 0:
        TOKEN = saved
        print("[SKIP] PERM   权限测试（成员登录失败）")
        return True
    TOKEN = data_of(p).get("token")

    c, p, r = call("GET", "/ci/jenkins-servers")
    denied = (c in (401, 403)) or (c == 200 and biz_code(p) != 0)
    record("PERM", "普通成员访问 Jenkins 配置被拒绝", denied, "HTTP %s %s" % (c, str(p)[:150]))

    c, p, r = call("GET", "/users")
    denied = (c in (401, 403)) or (c == 200 and biz_code(p) != 0)
    record("PERM", "普通成员访问用户管理被拒绝", denied, "HTTP %s %s" % (c, str(p)[:150]))

    # 成员仍可访问普通业务接口
    c, p, r = call("GET", "/projects")
    record("PERM", "普通成员可访问项目列表", c == 200 and biz_code(p) == 0, "HTTP %s" % c)

    TOKEN = saved
    return True


# ---------------------------------------------------------------- B 项目
def t_project():
    c, p, r = call("POST", "/projects", {"name": "功能测试项目", "description": "自动化测试创建"})
    if not check("PROJ", "创建项目", c, p, r):
        return False
    pid = data_of(p).get("id")
    _ctx["projectId"] = pid
    _ctx["projectId2"] = None

    c, p, r = call("GET", "/projects", params={"keyword": "功能测试"})
    check("PROJ", "项目列表(关键字搜索)", c, p, r)

    c, p, r = call("GET", "/projects/%s" % pid)
    check("PROJ", "项目详情", c, p, r)

    c, p, r = call("PUT", "/projects/%s" % pid, {"name": "功能测试项目(改)", "description": "已更新"})
    check("PROJ", "更新项目", c, p, r)

    c, p, r = call("POST", "/projects", {"name": "   ", "description": ""})
    check("PROJ", "空名称被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)

    # 不存在的项目
    c, p, r = call("GET", "/projects/not-exist-id")
    check("PROJ", "不存在项目返回错误", c, p, r, ok_codes=(200, 404, 500), expect_biz_fail=True)

    # 第二个项目：用于数据隔离校验
    c, p, r = call("POST", "/projects", {"name": "隔离测试项目B", "description": "用于数据隔离校验"})
    if c == 200:
        _ctx["projectId2"] = data_of(p).get("id")
    return True


# ---------------------------------------------------------------- C 环境
def t_environment():
    pid = _ctx["projectId"]
    c, p, r = call("POST", "/environments", {
        "projectId": pid, "name": "测试环境", "baseUrl": "http://127.0.0.1:8080",
        "variables": json.dumps([{"key": "uid", "value": "1"}]),
        "headers": json.dumps([{"key": "X-Test", "value": "1"}])
    })
    if not check("ENV", "创建环境", c, p, r):
        return False
    eid = data_of(p).get("id")
    _ctx["envId"] = eid

    c, p, r = call("GET", "/environments", params={"projectId": pid})
    check("ENV", "环境列表", c, p, r)
    lst = data_of(p) or []
    record("ENV", "环境列表返回数据", len(lst) > 0, "返回 %s 条" % len(lst))

    c, p, r = call("PUT", "/environments/%s" % eid, {
        "projectId": pid, "name": "测试环境(改)", "baseUrl": "http://127.0.0.1:8080",
        "variables": "[]", "headers": "[]"
    })
    check("ENV", "更新环境", c, p, r)

    c, p, r = call("POST", "/environments", {"name": "缺少项目ID"})
    check("ENV", "缺 projectId 被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)
    return True


# ---------------------------------------------------------------- D 全局变量
def t_global_variable():
    pid = _ctx["projectId"]
    c, p, r = call("POST", "/global-variables", {
        "projectId": pid, "name": "baseToken", "type": "text", "value": "abc123"
    })
    if not check("VAR", "创建普通变量", c, p, r):
        return False
    d = data_of(p)
    _ctx["varId"] = d.get("id") if isinstance(d, dict) else None

    c, p, r = call("POST", "/global-variables", {
        "projectId": pid, "name": "secretKey", "type": "secret", "value": "top-secret"
    })
    if not check("VAR", "创建加密变量", c, p, r):
        return False
    d = data_of(p)
    secret_id = d.get("id") if isinstance(d, dict) else None
    _ctx["secretVarId"] = secret_id

    c, p, r = call("GET", "/global-variables", params={"projectId": pid})
    check("VAR", "变量列表", c, p, r)
    lst = data_of(p) or []
    leaked = [v for v in lst if isinstance(v, dict)
              and v.get("name") == "secretKey" and v.get("value") == "top-secret"]
    record("VAR", "加密变量不回显明文", not leaked, "明文泄漏: %s" % leaked)

    if secret_id:
        c, p, r = call("PUT", "/global-variables/%s" % secret_id, {
            "projectId": pid, "name": "secretKey", "type": "secret", "value": "new-secret"
        })
        check("VAR", "更新加密变量", c, p, r)
        c, p, r = call("DELETE", "/global-variables/%s" % secret_id)
        check("VAR", "删除加密变量", c, p, r)

    # 重名变量
    c, p, r = call("POST", "/global-variables", {
        "projectId": pid, "name": "baseToken", "type": "text", "value": "dup"
    })
    check("VAR", "重复变量名处理", c, p, r, ok_codes=(200, 400, 500))
    return True


# ---------------------------------------------------------------- E 模块
def t_module():
    pid = _ctx["projectId"]
    c, p, r = call("POST", "/modules", {"projectId": pid, "name": "测试模块", "type": "case", "sortOrder": 1})
    if not check("MOD", "创建模块", c, p, r):
        return False
    _ctx["moduleId"] = data_of(p).get("id")

    c, p, r = call("GET", "/modules/tree", params={"projectId": pid})
    check("MOD", "模块树", c, p, r)
    tree = data_of(p)
    record("MOD", "模块树返回数据", bool(tree), "返回: %s" % str(tree)[:120])

    c, p, r = call("GET", "/modules/tree", params={"projectId": pid, "type": "case"})
    check("MOD", "模块树(按类型过滤)", c, p, r)
    return True


# ---------------------------------------------------------------- F 接口定义
def t_api_definition():
    pid = _ctx["projectId"]
    c, p, r = call("POST", "/apis", {
        "projectId": pid, "name": "健康检查接口", "method": "GET", "path": "/actuator/health",
        "headers": "[]", "query": "[]", "bodyType": "none", "description": "测试用"
    })
    if not check("API", "创建接口定义", c, p, r):
        return False
    aid = data_of(p).get("id")
    _ctx["apiId"] = aid

    c, p, r = call("GET", "/apis", params={"projectId": pid})
    check("API", "接口列表", c, p, r)
    lst = data_of(p)
    rows = lst if isinstance(lst, list) else (lst.get("records") if isinstance(lst, dict) else [])
    record("API", "接口列表返回数据", bool(rows), "返回 %s 条" % (len(rows) if rows else 0))

    c, p, r = call("GET", "/apis/%s" % aid)
    check("API", "接口详情", c, p, r)

    c, p, r = call("PUT", "/apis/%s" % aid, {
        "projectId": pid, "name": "健康检查接口(改)", "method": "GET", "path": "/actuator/health"
    })
    check("API", "更新接口", c, p, r)

    c, p, r = call("POST", "/apis/%s/generate-case" % aid)
    if check("API", "由接口生成用例", c, p, r):
        d = data_of(p)
        if isinstance(d, dict) and d.get("id"):
            _ctx["genCaseId"] = d["id"]

    # 无源 URL 的同步应给出明确业务错误
    c, p, r = call("POST", "/apis/%s/sync" % aid)
    check("API", "无源URL同步给出明确报错", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)

    # 非法 HTTP 方法
    c, p, r = call("POST", "/apis", {
        "projectId": pid, "name": "非法方法接口", "method": "INVALID", "path": "/x"
    })
    check("API", "非法HTTP方法处理", c, p, r, ok_codes=(200, 400, 500))
    return True


# ---------------------------------------------------------------- G 用例
def t_case():
    pid = _ctx["projectId"]
    steps = [
        {"name": "请求健康检查", "type": "http", "method": "GET",
         "url": "http://127.0.0.1:8080/api/v1/projects",
         "headers": [], "body": "",
         "assertions": [{"type": "status", "operator": "eq", "expected": "200"}]}
    ]
    c, p, r = call("POST", "/cases", {
        "projectId": pid, "name": "接口自动化测试用例", "description": "功能测试",
        "status": "draft", "priority": "P1", "tags": "[]",
        "steps": json.dumps(steps, ensure_ascii=False)
    })
    if not check("CASE", "创建用例", c, p, r):
        return False
    cid = data_of(p).get("id")
    _ctx["caseId"] = cid

    c, p, r = call("GET", "/cases/%s" % cid)
    check("CASE", "用例详情", c, p, r)

    c, p, r = call("GET", "/cases", params={"projectId": pid})
    check("CASE", "用例列表", c, p, r)
    lst = data_of(p)
    rows = lst if isinstance(lst, list) else (lst.get("records") if isinstance(lst, dict) else [])
    record("CASE", "用例列表返回数据", bool(rows), "返回 %s 条" % (len(rows) if rows else 0))

    c, p, r = call("PUT", "/cases/%s" % cid, {
        "projectId": pid, "name": "接口自动化测试用例(改)", "status": "draft",
        "priority": "P0", "steps": json.dumps(steps, ensure_ascii=False)
    })
    check("CASE", "更新用例", c, p, r)

    c, p, r = call("GET", "/cases/%s/versions" % cid)
    check("CASE", "用例版本列表", c, p, r)

    c, p, r = call("POST", "/cases/%s/review" % cid, {"result": "pass", "comment": "自动化评审"})
    check("CASE", "提交评审", c, p, r)

    c, p, r = call("GET", "/cases/%s/reviews" % cid)
    check("CASE", "评审记录列表", c, p, r)

    c, p, r = call("POST", "/cases/%s/debug" % cid, {})
    check("CASE", "用例调试执行", c, p, r, ok_codes=(200, 500))

    c, p, r = call("GET", "/cases/%s/debug-records" % cid)
    check("CASE", "调试记录列表", c, p, r)

    # 软删除 -> 回收站 -> 恢复
    c, p, r = call("DELETE", "/cases/%s" % cid)
    check("CASE", "删除用例(软删)", c, p, r)

    c, p, r = call("GET", "/cases", params={"projectId": pid})
    lst = data_of(p)
    rows = lst if isinstance(lst, list) else (lst.get("records") if isinstance(lst, dict) else [])
    still = [x for x in (rows or []) if x.get("id") == cid]
    record("CASE", "删除后不出现在列表", not still, "仍可见")

    c, p, r = call("GET", "/cases/recycle-bin", params={"projectId": pid})
    check("CASE", "回收站列表", c, p, r)

    c, p, r = call("POST", "/cases/%s/restore" % cid)
    check("CASE", "恢复用例", c, p, r)

    c, p, r = call("GET", "/cases", params={"projectId": pid})
    lst = data_of(p)
    rows = lst if isinstance(lst, list) else (lst.get("records") if isinstance(lst, dict) else [])
    back = [x for x in (rows or []) if x.get("id") == cid]
    record("CASE", "恢复后重新可见", bool(back), "未恢复")
    return True


# ---------------------------------------------------------------- H 场景
def t_scenario():
    pid = _ctx["projectId"]
    c, p, r = call("POST", "/scenarios", {
        "projectId": pid, "name": "测试场景", "description": "功能测试场景"
    })
    if not check("SCEN", "创建场景", c, p, r):
        return False
    sid = data_of(p).get("id")
    _ctx["scenarioId"] = sid

    c, p, r = call("GET", "/scenarios", params={"projectId": pid})
    check("SCEN", "场景列表", c, p, r)

    c, p, r = call("GET", "/scenarios/%s" % sid)
    check("SCEN", "场景详情", c, p, r)

    c, p, r = call("PUT", "/scenarios/%s" % sid, {"projectId": pid, "name": "测试场景(改)"})
    check("SCEN", "更新场景", c, p, r)

    c, p, r = call("POST", "/scenarios/%s/execute" % sid, {})
    check("SCEN", "执行场景", c, p, r, ok_codes=(200, 500))
    return True


# ---------------------------------------------------------------- I 测试任务（接口自动化）
def t_test_task():
    pid = _ctx["projectId"]
    c, p, r = call("POST", "/test-tasks", {
        "projectId": pid, "name": "接口自动化测试任务", "description": "功能测试",
        "caseIds": json.dumps([_ctx.get("caseId")]),
        "environmentId": _ctx.get("envId"),
        "executeMode": "sequential", "failStrategy": "continue_all",
        "parallelPoolSize": 1, "retryCount": 0, "timeoutMs": 30000, "enabled": True,
        "variables": "[]", "notifyChannels": "[]"
    })
    if not check("TASK", "创建测试任务", c, p, r):
        return False
    tid = data_of(p).get("id")
    _ctx["taskId"] = tid

    c, p, r = call("GET", "/test-tasks", params={"projectId": pid})
    check("TASK", "任务列表", c, p, r)

    c, p, r = call("GET", "/test-tasks/%s" % tid)
    check("TASK", "任务详情", c, p, r)

    c, p, r = call("PUT", "/test-tasks/%s" % tid, {
        "projectId": pid, "name": "接口自动化测试任务(改)", "executeMode": "sequential",
        "caseIds": json.dumps([_ctx.get("caseId")])
    })
    check("TASK", "更新任务", c, p, r)

    # 非法执行模式
    c, p, r = call("POST", "/test-tasks", {
        "projectId": pid, "name": "非法模式任务", "executeMode": "serial",
        "caseIds": json.dumps([_ctx.get("caseId")])
    })
    check("TASK", "非法执行模式被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)

    print("      ... 同步执行接口任务（最长 120s）")
    t0 = time.time()
    c, p, r = call("POST", "/test-tasks/%s/run-sync" % tid, {})
    check("TASK", "同步执行任务", c, p, r, ok_codes=(200, 500))
    print("      ... 耗时 %.1fs" % (time.time() - t0))
    if c == 200:
        d = data_of(p)
        if isinstance(d, dict):
            _ctx["taskRunId"] = d.get("id") or d.get("runId")
            print("      ... 结果: %s" % str({k: d.get(k) for k in ("id", "result", "status", "duration")})[:200])

    c, p, r = call("GET", "/test-tasks/%s/runs" % tid)
    check("TASK", "任务执行记录", c, p, r)
    runs = data_of(p) or []
    record("TASK", "执行记录有数据", len(runs) > 0, "返回 %s 条" % len(runs))

    c, p, r = call("POST", "/test-tasks/%s/run" % tid, {})
    check("TASK", "异步执行任务", c, p, r, ok_codes=(200, 500))

    time.sleep(2)
    c, p, r = call("POST", "/test-tasks/%s/toggle" % tid)
    check("TASK", "启停任务", c, p, r)

    c, p, r = call("POST", "/test-tasks/%s/webhook/rotate" % tid)
    check("TASK", "生成任务 Webhook Token", c, p, r)
    if c == 200:
        d = data_of(p)
        _ctx["taskToken"] = d.get("token") if isinstance(d, dict) else None

    if _ctx.get("taskToken"):
        # Controller 约定 token 为 query 参数（便于 curl 命令行调用）
        # 先验证频率限制：紧接着上一次触发应被限流
        c0, p0, r0 = call("POST", "/webhook/test-tasks/trigger",
                          params={"token": _ctx["taskToken"], "triggerBy": "rate-limit-probe"}, auth=False)
        limited = (c0 == 200 and "频繁" in str(p0)) or c0 in (400, 429)
        record("TASK", "Webhook 触发频率限制生效", limited, "HTTP %s: %s" % (c0, r0[:150]))

        # 等待限流窗口过去后正常触发
        time.sleep(6)
        c, p, r = call("POST", "/webhook/test-tasks/trigger",
                       params={"token": _ctx["taskToken"], "triggerBy": "functional-test"}, auth=False)
        check("TASK", "Webhook 免登录触发", c, p, r, ok_codes=(200, 500))

        # 错误 token 必须拒绝
        c, p, r = call("POST", "/webhook/test-tasks/trigger",
                       params={"token": "bad-token", "triggerBy": "x"}, auth=False)
        check("TASK", "Webhook 错误Token被拒绝", c, p, r,
              ok_codes=(200, 400, 401, 403, 404, 500), expect_biz_fail=True)

    c, p, r = call("POST", "/test-tasks/%s/webhook/disable" % tid)
    check("TASK", "禁用任务 Webhook", c, p, r)
    return True


# ---------------------------------------------------------------- J 报告
def t_report():
    pid = _ctx["projectId"]
    c, p, r = call("GET", "/reports", params={"projectId": pid, "page": 1, "size": 10})
    check("RPT", "报告列表", c, p, r)
    lst = data_of(p)
    rows = (lst if isinstance(lst, list)
            else (lst.get("records") or lst.get("list") or []) if isinstance(lst, dict) else [])
    _ctx["reportId"] = rows[0].get("id") if rows else None

    c, p, r = call("GET", "/reports/trend", params={"projectId": pid})
    check("RPT", "报告趋势", c, p, r)

    c, p, r = call("GET", "/reports/summary", params={"projectId": pid})
    check("RPT", "报告汇总", c, p, r)

    if not _ctx.get("reportId"):
        print("[SKIP] RPT    报告详情/分享（无报告数据）")
        return True
    rid = _ctx["reportId"]

    c, p, r = call("GET", "/reports/%s" % rid)
    check("RPT", "报告详情", c, p, r)

    c, p, r = call("GET", "/reports/%s/details" % rid)
    check("RPT", "报告明细", c, p, r)

    c, p, r = call("GET", "/reports/%s/export/html" % rid)
    record("RPT", "导出 HTML 报告", c == 200 and "</html>" in (r or "").lower(), "HTTP %s" % c)

    # 分享
    c, p, r = call("POST", "/reports/%s/share" % rid, {"expireHours": 24})
    check("RPT", "创建分享链接", c, p, r, ok_codes=(200, 500))
    token = None
    if c == 200 and biz_code(p) == 0:
        d = data_of(p)
        if isinstance(d, dict):
            token = d.get("token")
            _ctx["shareId"] = d.get("shareId") or d.get("id")

    c, p, r = call("GET", "/reports/%s/shares" % rid)
    check("RPT", "分享列表", c, p, r)

    if token:
        c, p, r = call("GET", "/public/reports/%s" % token, auth=False)
        ok_public = (c == 200 and biz_code(p) == 0)
        record("RPT", "公开链接免登录访问报告", ok_public, "HTTP %s: %s" % (c, r[:200]))
        if ok_public:
            # 错误 token 应失败
            c2, p2, r2 = call("GET", "/public/reports/invalid-token-zzz", auth=False)
            check("RPT", "公开链接错误token被拒绝", c2, p2, r2,
                  ok_codes=(200, 400, 401, 404, 500), expect_biz_fail=True)

    if _ctx.get("shareId"):
        c, p, r = call("POST", "/reports/%s/share/%s/revoke" % (rid, _ctx["shareId"]))
        check("RPT", "撤销分享", c, p, r, ok_codes=(200, 500))
        if token:
            c, p, r = call("GET", "/public/reports/%s" % token, auth=False)
            revoked = (c == 200 and biz_code(p) != 0) or c in (401, 404)
            record("RPT", "撤销后分享链接失效", revoked, "HTTP %s: %s" % (c, r[:150]))
    return True


# ---------------------------------------------------------------- K UI 自动化
def t_ui():
    pid = _ctx["projectId"]
    steps = [{"action": "open", "url": "http://127.0.0.1:8080/", "segment": "arrange"}]
    c, p, r = call("POST", "/ui/tests", {
        "projectId": pid, "name": "UI 测试用例", "description": "功能测试",
        "steps": json.dumps(steps, ensure_ascii=False)
    })
    if not check("UI", "创建 UI 用例", c, p, r):
        return False
    uid = data_of(p).get("id")
    _ctx["uiTestId"] = uid

    c, p, r = call("GET", "/ui/tests", params={"projectId": pid})
    check("UI", "UI 用例列表", c, p, r)
    lst = data_of(p) or []
    record("UI", "UI 用例列表有数据", len(lst) > 0, "返回 %s 条" % len(lst))

    c, p, r = call("GET", "/ui/tests/%s" % uid)
    check("UI", "UI 用例详情", c, p, r)

    c, p, r = call("PUT", "/ui/tests/%s" % uid, {
        "projectId": pid, "name": "UI 测试用例(改)", "steps": json.dumps(steps, ensure_ascii=False)
    })
    check("UI", "更新 UI 用例", c, p, r)

    c, p, r = call("POST", "/ui/scenarios", {
        "projectId": pid, "name": "UI 测试场景", "description": "功能测试",
        "steps": json.dumps(steps, ensure_ascii=False)
    })
    if not check("UI", "创建 UI 场景", c, p, r):
        return False
    usid = data_of(p).get("id")
    _ctx["uiScenarioId"] = usid

    c, p, r = call("GET", "/ui/scenarios", params={"projectId": pid})
    check("UI", "UI 场景列表", c, p, r)

    c, p, r = call("GET", "/ui/scenarios/%s" % usid)
    check("UI", "UI 场景详情", c, p, r)

    # 执行（本机若无可用的 ChromeDriver，预期以 error 终态结束，验证状态机能收敛）
    print("      ... 执行 UI 用例并轮询终态（最长 150s）")
    c, p, r = call("POST", "/ui/tests/%s/run" % uid, {})
    check("UI", "提交 UI 用例执行", c, p, r, ok_codes=(200, 500))
    if c == 200:
        d = data_of(p)
        rid = d.get("id") if isinstance(d, dict) else None
        _ctx["uiReportId"] = rid
        final = wait_until(
            lambda: data_of(call("GET", "/ui/reports", params={"projectId": pid})[1]) or [],
            lambda rows: any(x.get("id") == rid and x.get("status") in ("success", "failed", "error")
                             for x in (rows or [])),
            timeout=150, interval=3, label="UI执行")
        row = next((x for x in (final or []) if x.get("id") == rid), None)
        st = row.get("status") if row else None
        record("UI", "UI 执行收敛到终态", st in ("success", "failed", "error"), "状态=%s" % st)
        print("      ... UI 报告状态=%s duration=%s" % (st, (row or {}).get("duration")))

    c, p, r = call("POST", "/ui/scenarios/%s/run" % usid, {})
    check("UI", "提交 UI 场景执行", c, p, r, ok_codes=(200, 500))

    c, p, r = call("GET", "/ui/reports", params={"projectId": pid})
    check("UI", "UI 报告列表", c, p, r)
    return True


# ---------------------------------------------------------------- L 性能测试
def t_perf():
    pid = _ctx["projectId"]
    # 用无需认证的静态资源做压测目标，确保能产生成功样本
    steps = [{
        "name": "静态资源请求", "method": "GET", "url": "http://127.0.0.1:8080/",
        "headers": [], "body": ""
    }]
    c, p, r = call("POST", "/perf/cases", {
        "projectId": pid, "name": "性能测试用例", "description": "功能测试",
        "threads": 2, "rampUp": 1, "loops": 1, "duration": 0, "thinkTime": 0,
        "onSampleError": "continue", "variables": "[]",
        "steps": json.dumps(steps, ensure_ascii=False),
        "profile": json.dumps({"loadProfile": "fixed"})
    })
    if not check("PERF", "创建性能用例", c, p, r):
        return False
    pcid = data_of(p).get("id")
    _ctx["perfCaseId"] = pcid

    c, p, r = call("GET", "/perf/cases", params={"projectId": pid})
    check("PERF", "性能用例列表", c, p, r)
    lst = data_of(p) or []
    record("PERF", "性能用例列表有数据", len(lst) > 0, "返回 %s 条" % len(lst))

    c, p, r = call("GET", "/perf/cases/%s" % pcid)
    check("PERF", "性能用例详情", c, p, r)

    c, p, r = call("PUT", "/perf/cases/%s" % pcid, {
        "projectId": pid, "name": "性能测试用例(改)", "threads": 3, "rampUp": 1, "loops": 1,
        "steps": json.dumps(steps, ensure_ascii=False)
    })
    check("PERF", "更新性能用例", c, p, r)

    c, p, r = call("GET", "/perf/cases/%s/export-jmx" % pcid)
    record("PERF", "导出 JMX", c == 200 and "<jmeterTestPlan" in (r or ""), "HTTP %s" % c)

    print("      ... 执行性能用例并轮询终态（最长 180s）")
    t0 = time.time()
    c, p, r = call("POST", "/perf/cases/%s/run" % pcid, {})
    check("PERF", "提交性能用例执行", c, p, r, ok_codes=(200, 500))
    if c == 200:
        d = data_of(p)
        rid = d.get("id") if isinstance(d, dict) else None
        _ctx["perfReportId"] = rid
        final = wait_until(
            lambda: data_of(call("GET", "/perf/reports/%s" % rid)[1]),
            lambda x: isinstance(x, dict) and x.get("status") in ("success", "failed", "error"),
            timeout=180, interval=3, label="性能执行")
        st = final.get("status") if isinstance(final, dict) else None
        record("PERF", "性能执行收敛到终态", st in ("success", "failed", "error"), "状态=%s" % st)
        if isinstance(final, dict):
            print("      ... 性能报告 status=%s duration=%s summary=%s"
                  % (st, final.get("duration"), str(final.get("summary"))[:200]))
            try:
                summary = json.loads(final.get("summary") or "{}")
            except Exception:
                summary = {}
            record("PERF", "性能汇总含样本统计",
                   isinstance(summary, dict) and summary.get("sampleCount", 0) > 0,
                   "summary=%s" % str(summary)[:200])

    c, p, r = call("GET", "/perf/reports", params={"projectId": pid})
    check("PERF", "性能报告列表", c, p, r)

    if _ctx.get("perfReportId"):
        c, p, r = call("GET", "/perf/reports/%s" % _ctx["perfReportId"])
        check("PERF", "性能报告详情", c, p, r)
    return True


# ---------------------------------------------------------------- M 回收站
def t_recycle_bin():
    pid = _ctx["projectId"]
    c, p, r = call("GET", "/cases/recycle-bin", params={"projectId": pid})
    check("BIN", "回收站列表", c, p, r)

    c, p, r = call("GET", "/recycle-bin/config", params={"projectId": pid})
    check("BIN", "回收站配置读取", c, p, r)

    c, p, r = call("PUT", "/recycle-bin/config", {"projectId": pid, "cleanupDays": 30, "enabled": True})
    check("BIN", "回收站配置更新", c, p, r, ok_codes=(200, 500))

    c, p, r = call("PUT", "/recycle-bin/config", {"projectId": pid, "cleanupDays": -1})
    check("BIN", "非法清理天数被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)

    c, p, r = call("PUT", "/recycle-bin/config", {"cleanupDays": 7})
    check("BIN", "缺 projectId 被拒绝", c, p, r, ok_codes=(200, 400, 500), expect_biz_fail=True)
    return True


# ---------------------------------------------------------------- N 审计日志
def t_audit():
    c, p, r = call("GET", "/audit-logs", params={"page": 1, "size": 10})
    check("AUDIT", "审计日志列表", c, p, r)
    d = data_of(p)
    rows = (d if isinstance(d, list)
            else (d.get("records") or d.get("list") or []) if isinstance(d, dict) else [])
    record("AUDIT", "审计日志有记录", len(rows) > 0, "返回 %s 条" % len(rows))
    return True


# ---------------------------------------------------------------- O 执行机
def t_executor():
    c, p, r = call("GET", "/executors/pool")
    check("EXEC", "执行机资源池", c, p, r)

    c, p, r = call("POST", "/executors/heartbeat", {
        "nodeId": "functional-test-node", "host": "127.0.0.1", "port": 9090, "status": "idle"
    })
    check("EXEC", "执行机心跳上报", c, p, r, ok_codes=(200, 500))

    c, p, r = call("GET", "/executors/pool")
    check("EXEC", "心跳后资源池查询", c, p, r)

    c, p, r = call("POST", "/executors/functional-test-node/release")
    check("EXEC", "释放执行机", c, p, r, ok_codes=(200, 404, 500))
    return True


# ---------------------------------------------------------------- P CI/CD
def t_ci():
    pid = _ctx["projectId"]
    # Jenkins 服务器
    c, p, r = call("POST", "/ci/jenkins-servers", {
        "name": "本地Jenkins(不可达)", "baseUrl": "http://127.0.0.1:18080",
        "username": "admin", "apiToken": "fake-token", "authType": "basic", "enabled": True
    })
    if not check("CI", "创建 Jenkins 服务器", c, p, r):
        return False
    jid = data_of(p).get("id")
    _ctx["jenkinsId"] = jid

    c, p, r = call("GET", "/ci/jenkins-servers")
    check("CI", "Jenkins 服务器列表", c, p, r)
    lst = data_of(p) or []
    leaked = [s for s in lst if isinstance(s, dict) and s.get("apiToken")]
    record("CI", "Jenkins Token 不回显", not leaked, "Token 泄漏")

    c, p, r = call("GET", "/ci/jenkins-servers/%s" % jid)
    check("CI", "Jenkins 服务器详情", c, p, r)

    c, p, r = call("PUT", "/ci/jenkins-servers/%s" % jid, {
        "name": "本地Jenkins(改)", "baseUrl": "http://127.0.0.1:18080", "authType": "basic"
    })
    check("CI", "更新 Jenkins 服务器", c, p, r)

    print("      ... 连通性测试（目标不可达，应返回明确失败而非抛异常）")
    c, p, r = call("POST", "/ci/jenkins-servers/%s/test" % jid)
    check("CI", "Jenkins 连通性测试", c, p, r, ok_codes=(200, 500))
    d = data_of(p)
    if isinstance(d, dict):
        record("CI", "连通测试结果有明确结论", "success" in d, "返回: %s" % str(d)[:150])
        print("      ... 连通测试: %s" % str(d)[:150])

    c, p, r = call("GET", "/ci/jenkins-servers/%s/jobs" % jid)
    check("CI", "拉取 Jenkins Job 列表", c, p, r, ok_codes=(200, 500))

    # 本地执行流水线
    if not _ctx.get("taskId"):
        record("CI", "创建流水线(local)", False, "前置测试任务缺失")
        return True
    c, p, r = call("POST", "/ci/pipelines", {
        "projectId": pid, "name": "接口自动化流水线", "description": "功能测试",
        "testType": "api", "targetType": "test_task", "targetId": _ctx["taskId"],
        "execMode": "local", "enabled": True,
        "qualityGate": json.dumps([{"metric": "pass_rate", "operator": "lt", "threshold": 100}])
    })
    if not check("CI", "创建流水线(local)", c, p, r):
        return True
    plid = data_of(p).get("id")
    _ctx["pipelineId"] = plid

    c, p, r = call("GET", "/ci/pipelines", params={"projectId": pid})
    check("CI", "流水线列表", c, p, r)

    c, p, r = call("GET", "/ci/pipelines/%s" % plid)
    check("CI", "流水线详情", c, p, r)

    for tt in ("api", "ui", "perf"):
        c, p, r = call("GET", "/ci/pipelines/targets", params={"projectId": pid, "testType": tt})
        check("CI", "可选执行目标(%s)" % tt, c, p, r)
        print("      ... %s 目标: %s" % (tt, str(data_of(p))[:150]))

    c, p, r = call("PUT", "/ci/pipelines/%s" % plid, {"name": "接口自动化流水线(改)"})
    check("CI", "更新流水线", c, p, r)

    print("      ... 触发流水线（同步等待，最长 150s）")
    t0 = time.time()
    c, p, r = call("POST", "/ci/pipelines/%s/trigger" % plid,
                   {"wait": True, "triggerBy": "functional-test"})
    check("CI", "触发流水线执行", c, p, r, ok_codes=(200, 500))
    print("      ... 耗时 %.1fs" % (time.time() - t0))
    run_id = None
    if c == 200 and biz_code(p) == 0:
        d = data_of(p)
        run_id = d.get("id") if isinstance(d, dict) else None
        _ctx["ciRunId"] = run_id
        print("      ... status=%s gatePassed=%s" % (d.get("status"), d.get("gatePassed")))
        record("CI", "流水线执行收敛到终态",
               d.get("status") in ("success", "failed", "error"), "状态=%s" % d.get("status"))
        record("CI", "质量门禁已判定", d.get("gatePassed") is not None,
               "gatePassed=%s" % d.get("gatePassed"))
        print("      ... gateResult=%s" % str(d.get("gateResult"))[:250])
    else:
        print("      ... 返回: %s" % r[:300])

    c, p, r = call("GET", "/ci/pipelines/%s/runs" % plid, params={"limit": 10})
    check("CI", "流水线执行记录", c, p, r)
    runs = data_of(p) or []
    record("CI", "执行记录有数据", len(runs) > 0, "返回 %s 条" % len(runs))

    if run_id:
        c, p, r = call("GET", "/ci/pipelines/runs/%s" % run_id)
        check("CI", "执行记录详情", c, p, r)

    c, p, r = call("GET", "/ci/pipelines/%s/jenkinsfile" % plid)
    check("CI", "生成 Jenkinsfile", c, p, r)
    if c == 200:
        d = data_of(p)
        content = d.get("content") if isinstance(d, dict) else str(d)
        has = "pipeline" in (content or "").lower() and "stage" in (content or "").lower()
        record("CI", "Jenkinsfile 含 pipeline/stage", has, "内容异常")
        has_curl = bool((d or {}).get("curl")) if isinstance(d, dict) else False
        record("CI", "Jenkinsfile 附带 curl 示例", has_curl, "无 curl")

    # Webhook
    c, p, r = call("POST", "/ci/pipelines/%s/webhook/rotate" % plid)
    check("CI", "生成 CI Webhook Token", c, p, r)
    token = None
    if c == 200 and biz_code(p) == 0:
        d = data_of(p)
        token = d.get("token") if isinstance(d, dict) else None
        _ctx["ciToken"] = token

    if token:
        # 频率限制：紧接上一次触发应被限流
        c0, p0, r0 = call("POST", "/webhook/ci/pipelines/trigger",
                          params={"token": token, "triggerBy": "rate-limit-probe"}, auth=False)
        limited = (c0 == 200 and "频繁" in str(p0)) or c0 in (400, 429)
        record("CI", "CI Webhook 频率限制生效", limited, "HTTP %s: %s" % (c0, r0[:150]))

        time.sleep(6)
        c, p, r = call("POST", "/webhook/ci/pipelines/trigger",
                       params={"token": token, "triggerBy": "functional-test"}, auth=False)
        check("CI", "CI Webhook 免登录触发", c, p, r, ok_codes=(200, 500))
        if c == 200 and biz_code(p) == 0:
            d = data_of(p)
            wrid = d.get("runId") or d.get("id")
            print("      ... webhook 返回: %s" % str(d)[:200])
            if wrid:
                final = wait_until(
                    lambda: data_of(call("GET", "/webhook/ci/runs/%s" % wrid,
                                         params={"token": token}, auth=False)[1]),
                    lambda x: isinstance(x, dict) and x.get("status") in ("success", "failed", "error"),
                    timeout=120, interval=3, label="Webhook轮询")
                st = final.get("status") if isinstance(final, dict) else None
                record("CI", "Webhook 轮询收敛到终态",
                       st in ("success", "failed", "error"), "状态=%s" % st)

    c, p, r = call("POST", "/webhook/ci/pipelines/trigger",
                   params={"token": "invalid-token-xxx", "triggerBy": "hacker"}, auth=False)
    check("CI", "无效 Token 被拒绝", c, p, r,
          ok_codes=(200, 400, 401, 403, 404, 500), expect_biz_fail=True)

    c, p, r = call("POST", "/ci/pipelines/%s/toggle" % plid)
    check("CI", "流水线启停", c, p, r)

    # Jenkins 模式：不可达时应优雅失败而非 500 崩溃
    c, p, r = call("POST", "/ci/pipelines", {
        "projectId": pid, "name": "UI流水线(Jenkins模式)", "testType": "ui",
        "targetType": "ui_test", "targetId": _ctx.get("uiTestId"),
        "execMode": "jenkins", "jenkinsServerId": jid, "jenkinsJobName": "demo-job", "enabled": True
    })
    if check("CI", "创建流水线(jenkins模式)", c, p, r, ok_codes=(200, 500)) and c == 200:
        jpid = data_of(p).get("id")
        c2, p2, r2 = call("POST", "/ci/pipelines/%s/trigger" % jpid, {"wait": False})
        check("CI", "触发 Jenkins 模式流水线", c2, p2, r2, ok_codes=(200, 500))
        print("      ... Jenkins 模式触发: %s" % r2[:200])
        if c2 == 200 and biz_code(p2) == 0:
            d = data_of(p2)
            record("CI", "Jenkins 不可达时记录异常态",
                   d.get("status") in ("error", "failed", "running", "pending"),
                   "状态=%s" % d.get("status"))
        call("DELETE", "/ci/pipelines/%s" % jpid)

    # 测试类型与目标类型不匹配
    c, p, r = call("POST", "/ci/pipelines", {
        "projectId": pid, "name": "类型不匹配流水线", "testType": "perf",
        "targetType": "ui_test", "targetId": _ctx.get("uiTestId"), "execMode": "local"
    })
    check("CI", "测试类型与目标类型不匹配处理", c, p, r, ok_codes=(200, 400, 500))
    print("      ... 返回: %s" % r[:200])

    # 删除校验
    c, p, r = call("DELETE", "/ci/pipelines/%s" % plid)
    check("CI", "删除流水线", c, p, r, ok_codes=(200, 500))
    c, p, r = call("GET", "/ci/pipelines", params={"projectId": pid})
    lst = data_of(p) or []
    gone = not [x for x in lst if x.get("id") == plid]
    record("CI", "删除后不再出现在列表", gone, "仍可见")

    c, p, r = call("DELETE", "/ci/jenkins-servers/%s" % jid)
    check("CI", "删除 Jenkins 服务器", c, p, r, ok_codes=(200, 500))
    return True


# ---------------------------------------------------------------- Q 数据隔离
def t_isolation():
    """项目 A 的数据不应出现在项目 B 的查询结果中。"""
    if not _ctx.get("projectId2"):
        print("[SKIP] ISO   数据隔离（无第二个项目）")
        return True
    pid2 = _ctx["projectId2"]
    for label, path in (("用例", "/cases"), ("接口", "/apis"), ("场景", "/scenarios"),
                        ("环境", "/environments"), ("全局变量", "/global-variables")):
        c, p, r = call("GET", path, params={"projectId": pid2})
        if c != 200 or biz_code(p) != 0:
            record("ISO", "%s列表(项目B)" % label, False, "HTTP %s: %s" % (c, r[:150]))
            continue
        lst = data_of(p) or []
        record("ISO", "%s按项目隔离" % label, len(lst) == 0,
               "项目B 出现 %s 条数据: %s" % (len(lst), str(lst)[:120]))

    c, p, r = call("GET", "/ci/pipelines", params={"projectId": pid2})
    lst = data_of(p) or []
    record("ISO", "流水线按项目隔离", len(lst) == 0, "项目B 出现 %s 条" % len(lst))
    return True


# ---------------------------------------------------------------- R 错误处理
def t_error_handling():
    """客户端参数错误应返回 400 并指出具体参数，而不是笼统的 500。"""
    pid = _ctx["projectId"]
    # 缺少必填 query 参数：应给出业务错误并指明参数名（不再是笼统的 500 内部错误）
    c, p, r = call("GET", "/environments")
    ok = biz_code(p) == 1001 and "projectId" in str(p)
    record("ERR", "缺必填参数明确指明参数名", ok, "HTTP %s: %s" % (c, r[:200]))

    # 参数类型不匹配
    c, p, r = call("GET", "/cases/%s/versions/abc/restore" % (_ctx.get("caseId") or "x"),
                   body={}) if False else (0, None, "")
    c, p, r = call("POST", "/cases/%s/versions/not-a-number/restore" % (_ctx.get("caseId") or "x"), {})
    ok = (c == 400) or (c == 200 and biz_code(p) != 0)
    record("ERR", "路径参数类型错误返回400", ok, "HTTP %s: %s" % (c, r[:200]))

    # 请求体 JSON 非法（通过底层请求构造）
    import urllib.request as _u
    req = _u.Request(BASE + "/projects", data=b"{not-json",
                     headers={"Content-Type": "application/json",
                              "Authorization": "Bearer " + TOKEN}, method="POST")
    try:
        with _u.urlopen(req, timeout=30) as resp:
            code, raw = resp.getcode(), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        code, raw = e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        code, raw = 0, str(e)
    ok = (code == 200 and '"code":1001' in raw.replace(" ", "")) or code == 400
    record("ERR", "非法JSON请求体给出明确错误", ok, "HTTP %s: %s" % (code, raw[:200]))

    # 不存在的资源
    c, p, r = call("GET", "/cases/definitely-not-exist")
    ok = (c == 200 and biz_code(p) != 0) or c in (404, 400, 500)
    record("ERR", "不存在资源给出明确错误", ok, "HTTP %s: %s" % (c, r[:150]))
    return True


# ---------------------------------------------------------------- 清理
def cleanup():
    for pid in (_ctx.get("projectId"), _ctx.get("projectId2")):
        if pid:
            call("DELETE", "/projects/%s" % pid)
    if _ctx.get("memberName"):
        # 管理员删除测试成员
        c, p, r = call("GET", "/users")
        d = data_of(p) or []
        for u in (d if isinstance(d, list) else []):
            if u.get("username") == _ctx["memberName"]:
                call("DELETE", "/users/%s" % u.get("id"))


# ---------------------------------------------------------------- 主流程
def main():
    modules = [
        ("认证", t_auth),
        ("权限", t_permission),
        ("项目", t_project),
        ("环境", t_environment),
        ("全局变量", t_global_variable),
        ("模块", t_module),
        ("接口定义", t_api_definition),
        ("用例", t_case),
        ("场景", t_scenario),
        ("测试任务", t_test_task),
        ("报告", t_report),
        ("UI自动化", t_ui),
        ("性能测试", t_perf),
        ("回收站", t_recycle_bin),
        ("审计日志", t_audit),
        ("执行机", t_executor),
        ("CI/CD", t_ci),
        ("数据隔离", t_isolation),
        ("错误处理", t_error_handling),
    ]
    print("=" * 72)
    print("codex-apiweb 全系统功能测试  BASE=%s" % BASE)
    print("=" * 72)
    for label, fn in modules:
        print("\n---------- %s ----------" % label)
        try:
            fn()
        except Exception as e:
            import traceback
            results.append((label, "模块执行异常", False, traceback.format_exc()[-400:]))
            print("[FAIL] %s 模块异常: %s" % (label, e))

    print("\n---------- 清理测试数据 ----------")
    try:
        cleanup()
        print("已清理测试项目")
    except Exception as e:
        print("清理失败: %s" % e)

    print("\n" + "=" * 72)
    total = len(results)
    failed = [x for x in results if not x[2]]
    print("总计 %d 项，通过 %d 项，失败 %d 项" % (total, total - len(failed), len(failed)))
    if failed:
        print("\n失败明细：")
        for m, n, _, msg in failed:
            print("  - [%-8s] %s\n      %s" % (m, n, str(msg)[:400]))
    print("=" * 72)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
