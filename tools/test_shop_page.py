# -*- coding: utf-8 -*-
"""测试所有商铺类型的下拉分页接口"""
import json
import urllib.request

BASE = "http://127.0.0.1:8081/shop/of/type"


def query(type_id, current, with_geo=True):
    url = "%s?typeId=%d&current=%d" % (BASE, type_id, current)
    if with_geo:
        url += "&x=120.149993&y=30.334229"
    try:
        data = json.load(urllib.request.urlopen(url))
        return data.get("data", [])
    except Exception as e:
        return "ERROR: %s" % e


print("=== 带坐标（GEO 分支，模拟前端）===")
for t in range(1, 11):
    p1 = query(t, 1, True)
    if isinstance(p1, list):
        print("type=%d: P1=%d 家" % (t, len(p1)))
        if len(p1) == 5:
            p2 = query(t, 2, True)
            print("        P2=%d 家（下拉可加载更多）" % (len(p2) if isinstance(p2, list) else p2))
        elif len(p1) == 0:
            print("        P1 为空，第一屏就无数据")
    else:
        print("type=%d: %s" % (t, p1))

print()
print("=== 不带坐标（数据库分页）===")
for t in range(1, 11):
    p1 = query(t, 1, False)
    if isinstance(p1, list):
        print("type=%d: P1=%d 家" % (t, len(p1)))
    else:
        print("type=%d: %s" % (t, p1))
