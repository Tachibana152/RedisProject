# -*- coding: utf-8 -*-
"""
为全部 10 个商铺类型补充测试店铺数据（每类型 12 家，共约 120 家）
坐标围绕杭州城区（120.14~120.17, 30.30~30.34）随机分布，保证 GEO 15km 半径内可查
"""
import random
import subprocess
import sys

random.seed(42)

# 每个类型的店铺名称模板（中文）
TYPE_NAMES = {
    1: "美食", 2: "KTV", 3: "丽人·美发", 4: "健身运动",
    5: "按摩·足疗", 6: "美容SPA", 7: "亲子游乐", 8: "酒吧", 9: "轰趴馆", 10: "美睫·美甲",
}
BRANDS = {
    1: ["川味火锅", "粤式茶餐厅", "江浙本帮菜", "日式料理", "韩式烤肉", "西北面馆", "湘菜馆", "东北饺子馆", "港式甜品", "轻食沙拉", "咖啡烘焙", "海鲜酒楼"],
    2: ["量贩KTV", "主题KTV", "音乐KTV", "派对KTV", "欢唱KTV", "星光KTV", "唱吧KTV", "纯K", "麦乐迪", "金色年华", "魅KTV", "好乐迪"],
    3: ["美发沙龙", "造型工作室", "烫染专门店", "理发馆", "形象设计", "丝语美发", "风尚造型", "壹加壹美发", "发道", "慕名造型", "秀丝美发", "潮流剪吧"],
    4: ["健身工作室", "瑜伽馆", "搏击俱乐部", "游泳健身", "CrossFit", "普拉提馆", "篮球馆", "羽毛球馆", "台球俱乐部", "舞蹈教室", "单车房", "综合格斗馆"],
    5: ["足疗养生", "按摩推拿", "中医理疗", "肩颈SPA", "泰式按摩", "采耳馆", "修脚堂", "经络养生", "艾灸馆", "拔罐刮痧", "指压按摩", "足道会馆"],
    6: ["美容SPA", "皮肤管理中心", "护肤会所", "抗衰中心", "面部护理", "香薰SPA", "水疗会馆", "美体塑形", "科技美肤", "养生SPA", "亮肤馆", "私人护理"],
    7: ["亲子乐园", "儿童游乐场", "淘气堡", "室内游乐", "手工DIY", "乐高中心", "早教中心", "绘本馆", "儿童游泳", "蹦床公园", "积木乐园", "亲子烘焙坊"],
    8: ["静吧", "精酿啤酒吧", "鸡尾酒吧", "清吧", "livehouse", "威士忌吧", "民谣酒吧", "红酒屋", "英式酒吧", "日式居酒屋", "夜店", "音乐餐吧"],
    9: ["轰趴馆", "别墅聚会", "主题轰趴", "桌游吧", "剧本杀", "狼人杀俱乐部", "VR体验馆", "电竞馆", "密室逃脱", "家庭聚会馆", "团建基地", "棋牌室"],
    10: ["美甲工作室", "美睫工作室", "美甲美睫", "纹绣工作室", "手足护理", "半永久定妆", "美甲店", "睫毛嫁接", "日式美甲", "韩式美睫", "美甲沙龙", "化妆造型"],
}
AREAS = ["拱宸桥/上塘", "大关", "运河上街", "湖墅南路", "文晖路", "武林广场", "延安路", "西湖文化广场", "城西银泰", "西溪", "滨江", "钱江新城", "下沙", "九堡"]
ADDRESSES = [
    "湖墅南路{0}号", "文晖路{0}号", "大关路{0}号", "上塘路{0}号", "丽水路{0}号",
    "台州路{0}号", "拱墅区{0}号", "延安路{0}号", "凤起路{0}号", "庆春路{0}号",
    "武林路{0}号", "中山北路{0}号", "建国北路{0}号", "东新路{0}号",
]
OPEN_HOURS = ["10:00-22:00", "11:00-23:00", "09:00-21:00", "10:30-22:30", "24小时", "11:30-02:00", "08:00-20:00", "12:00-24:00"]

# 现有图片 URL（复用）
IMG_POOL = [
    "https://qcloud.dpfile.com/pc/jiclIsCKmOI2arxKN1Uf0Hx3PucIJH8q0QSz-Z8llzcN56-_QiKuOvyio1OOxsRtFoXqu0G3iT2T27qat3WhLVEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vfCF2ubeXzk49OsGrXt_KYDCngOyCwZK-s3fqawWswzk.jpg",
    "https://p0.meituan.net/bbia/c1870d570e73accbc9fee90b48faca41195272.jpg",
    "https://img.meituan.net/msmerchant/232f8fdf09050838bd33fb24e79f30f9606056.jpg",
    "https://img.meituan.net/msmerchant/054b5de0ba0b50c18a620cc37482129a45739.jpg",
    "https://img.meituan.net/msmerchant/876ca8983f7395556eda9ceb064e6bc51840883.png",
    "https://qcloud.dpfile.com/pc/MZTdRDqCZdbPDUO0Hk6lZENRKzpKRF7kavrkEI99OxqBZTzPfIxa5E33gBfGouhFuzFvxlbkWx5uwqY2qcjixFEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vmIU_8ZGOT1OjpJmLxG6urQ.jpg",
]


def gen_shop(type_id, idx):
    brand = BRANDS[type_id][idx % len(BRANDS[type_id])]
    name = "%s·%s" % (brand, "城西店" if idx % 3 == 0 else ("滨江店" if idx % 3 == 1 else "总店"))
    area = random.choice(AREAS)
    addr = random.choice(ADDRESSES).format(random.randint(1, 999))
    # 坐标：杭州城区范围，加上小偏移，保证同一类型店铺分散
    x = round(120.14 + random.uniform(0, 0.04), 6)
    y = round(30.28 + random.uniform(0, 0.06), 6)
    avg_price = random.randint(30, 400)
    sold = random.randint(100, 20000)
    comments = int(sold * random.uniform(0.4, 0.9))
    score = random.randint(35, 50)
    open_hours = random.choice(OPEN_HOURS)
    img = random.choice(IMG_POOL)
    return (
        name, type_id, img, area, addr, x, y, avg_price, sold, comments, score, open_hours
    )


def escape(s):
    return str(s).replace("'", "''")


def main():
    sql_lines = ["USE `tongcheng`;"]
    for type_id in TYPE_NAMES:
        for i in range(12):
            (name, tid, img, area, addr, x, y, avg, sold, comments, score, oh) = gen_shop(type_id, i)
            sql_lines.append(
                "INSERT INTO `tb_shop` (`name`,`type_id`,`images`,`area`,`address`,`x`,`y`,`avg_price`,`sold`,`comments`,`score`,`open_hours`,`create_time`,`update_time`) VALUES "
                "('%s', %d, '%s', '%s', '%s', %f, %f, %d, %d, %d, %d, '%s', NOW(), NOW());"
                % (escape(name), tid, escape(img), escape(area), escape(addr), x, y, avg, sold, comments, score, escape(oh))
            )
    sql_text = "\n".join(sql_lines) + "\n"

    # 输出 SQL 到临时文件（UTF-8）
    with open("target/seed_shops.sql", "w", encoding="utf-8") as f:
        f.write(sql_text)

    # 用 mysql 客户端 UTF-8 执行
    proc = subprocess.run(
        ["mysql", "-uroot", "-pa359938435", "--default-character-set=utf8mb4"],
        input=sql_text.encode("utf-8"),
        capture_output=True,
    )
    if proc.returncode != 0:
        print("导入失败:", proc.stderr.decode("utf-8", errors="replace"))
        sys.exit(1)
    print("导入成功")

    # 统计验证
    proc2 = subprocess.run(
        ["mysql", "-uroot", "-pa359938435", "--default-character-set=utf8mb4",
         "-e", "USE tongcheng; SELECT type_id, COUNT(*) FROM tb_shop GROUP BY type_id ORDER BY type_id;"],
        capture_output=True,
    )
    print(proc2.stdout.decode("utf-8", errors="replace"))


if __name__ == "__main__":
    main()
