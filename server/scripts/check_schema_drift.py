#!/usr/bin/env python3
"""比对 Java 实体字段与数据库实际列，找出缺失的列。

用法：python check_schema_drift.py
"""
import re
import os
import subprocess
import sys

ENTITY_DIR = r"C:\Users\Administrator\Desktop\codex-apiweb\server\src\main\java\com\apiweb\entity"
MYSQL = r"C:\dev\mysql-8.0.24-winx64\bin\mysql.exe"
DB_ARGS = ["-h127.0.0.1", "-P3306", "-uapiweb", "-papiweb123", "api_web", "-N", "-B"]


def camel_to_snake(name: str) -> str:
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", name)
    s = re.sub(r"([a-z\d])([A-Z])", r"\1_\2", s)
    return s.lower()


def get_java_fields(path: str):
    """返回 (表名, [列名...])；列名已转下划线，并应用 @TableField 显式映射。"""
    with open(path, encoding="utf-8") as f:
        src = f.read()

    m = re.search(r'@TableName\("([^"]+)"\)', src)
    if not m:
        return None
    table = m.group(1)

    cols = []
    # 逐行扫描：记录 @TableField 显式列名，以及 private 字段名
    pending_explicit = None
    for line in src.splitlines():
        tf = re.search(r'@TableField\("([^"]+)"\)', line)
        if tf:
            pending_explicit = tf.group(1)
            continue
        # 静态常量、serialVersionUID 等跳过
        fm = re.search(r'^\s*private\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],\s\.]+\s+(\w+)\s*;', line)
        if fm:
            name = fm.group(1)
            if name == "serialVersionUID":
                pending_explicit = None
                continue
            cols.append(pending_explicit if pending_explicit else camel_to_snake(name))
            pending_explicit = None
    return table, cols


def get_db_columns(table: str):
    out = subprocess.run(
        [MYSQL] + DB_ARGS + ["-e", f"SHOW COLUMNS FROM {table};"],
        capture_output=True, text=True
    )
    if out.returncode != 0:
        return None
    return {line.split("\t")[0] for line in out.stdout.strip().splitlines() if line.strip()}


def main():
    drift = []
    for fn in sorted(os.listdir(ENTITY_DIR)):
        if not fn.endswith(".java"):
            continue
        path = os.path.join(ENTITY_DIR, fn)
        parsed = get_java_fields(path)
        if not parsed:
            continue
        table, java_cols = parsed
        db_cols = get_db_columns(table)
        if db_cols is None:
            drift.append((table, fn, ["<表不存在>"], []))
            continue
        missing = [c for c in java_cols if c not in db_cols]
        if missing:
            drift.append((table, fn, missing, []))

    if not drift:
        print("OK：所有实体字段与数据库列完全一致")
        return 0

    print("发现结构漂移（Java 有、DB 无）：\n")
    for table, fn, missing, _ in drift:
        print(f"  表 {table}  ({fn})")
        for c in missing:
            print(f"      - 缺列: {c}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
