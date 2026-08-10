# -*- coding: utf-8 -*-
"""检查官方 hmdp.sql 中 tb_shop 的店铺数量与类型分布"""
import io
import re
import collections

path = r"C:\Users\Tachibana\Desktop\java_codee\Project-sekai-04-others\资料\hmdp.sql"
content = io.open(path, encoding="utf-8").read()

# 找出所有 tb_shop 的 INSERT 语句（可能跨多行）
pattern = re.compile(r"INSERT INTO `tb_shop` VALUES (.*?);", re.S)
matches = pattern.findall(content)
print("tb_shop INSERT 语句数:", len(matches))

# 统计 type_id（第 3 个字段）分布
type_counter = collections.Counter()
for m in matches:
    # 提取括号内的值
    inner = m.strip()
    # 简单解析：找 type_id 位置 —— 字段顺序 id, name, type_id, images, area, address, x, y, ...
    # 直接数逗号分隔，第3个字段（索引2）是 type_id
    parts = re.split(r",(?=(?:[^']*'[^']*')*[^']*$)", inner)
    if len(parts) > 2:
        type_id = parts[2].strip()
        type_counter[type_id] += 1

print("各 type_id 的店铺数:", dict(type_counter))
print("店铺总数:", sum(type_counter.values()))
