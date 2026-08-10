# -*- coding: utf-8 -*-
"""将 hmdp.sql 以 UTF-8 字节流方式导入 MySQL（绕过 PowerShell 编码问题）"""
import subprocess

SQL_FILE = r"C:\Users\Tachibana\Desktop\java_codee\Project-sekai-04-others\资料\hmdp.sql"

with open(SQL_FILE, "rb") as f:
    sql_bytes = f.read()

proc = subprocess.run(
    ["mysql", "-uroot", "-pa359938435", "--default-character-set=utf8mb4", "tongcheng"],
    input=sql_bytes,
    capture_output=True,
)
print("exit =", proc.returncode)
if proc.stderr:
    print("stderr:", proc.stderr.decode("utf-8", errors="replace")[:2000])
