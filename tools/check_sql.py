# -*- coding: utf-8 -*-
"""检查 hmdp.sql 中的店铺中文数据"""
import io
import re

path = r"C:\Users\Tachibana\Desktop\java_codee\Project-sekai-04-others\资料\hmdp.sql"
content = io.open(path, encoding="utf-8").read()

# 统计各表 INSERT 数量
for t in re.findall(r"INSERT INTO `(\w+)`", content):
    pass

# 找到 tb_shop 的 INSERT
m = re.search(r"INSERT INTO `tb_shop`.*?;", content, re.S)
if m:
    print("tb_shop INSERT 前 600 字符：")
    print(m.group(0)[:600])
else:
    print("未找到 tb_shop INSERT")
