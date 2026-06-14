import json
import urllib.request
import ssl
import re

API_BASE = "https://token-plan-cn.xiaomimimo.com/v1"
with open(".secrets/api_key.txt", "r") as f:
    API_KEY = f.read().strip()
MODEL = "mimo-v2.5-pro"

with open("C:/Users/Ashley Wilkes/AppData/Local/Temp/course_data.html", "r", encoding="utf-8") as f:
    html = f.read()

SYSTEM_PROMPT = """你是一个课程表解析助手。用户会给你一个教务系统课表页面的 HTML 片段。
请从中提取所有课程信息，返回一个 JSON 数组。

输出格式（严格遵循，不要添加任何其他文本）：
[{"name":"课程名称","teacher":"教师姓名","location":"上课地点","day":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2,3],"type":"all"}]

注意：
- day 取值：周一=1, 周二=2, ..., 周日=7
- startPeriod/endPeriod 是小节号（1-2为第1大节，3-4为第2大节，5-6为第3大节，7-8为第4大节，9-10为第5大节）
- HTML中"第N大节"对应的节次映射：第1大节=1-2, 第2大节=3-5, 第3大节=6-7, 第4大节=8-9, 第5大节=10-11(或11-12)
- 看HTML中每行<th>里的小节号来确定正确的startPeriod和endPeriod
- type: "all"=每周, "odd"=单周, "even"=双周
- 如果周次是连续的（如 1-16 周），展开为数组
- 注意识别"1-5,7-12周"这种跳过某些周的格式
- 有些课程可能出现在多个时间段（同一天不同节次，或不同天），每个时间段单独一条记录
- 只输出 JSON 数组，不要输出其他任何内容"""

request_body = json.dumps({
    "model": MODEL,
    "messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "请解析以下课表 HTML：\n\n" + html}
    ],
    "max_tokens": 8192
}, ensure_ascii=False).encode("utf-8")

ctx = ssl.create_default_context()
req = urllib.request.Request(
    API_BASE + "/chat/completions",
    data=request_body,
    headers={
        "Content-Type": "application/json",
        "Authorization": "Bearer " + API_KEY
    }
)

print("Sending to LLM (%d chars HTML)..." % len(html))
resp = urllib.request.urlopen(req, context=ctx, timeout=120)
data = json.loads(resp.read().decode("utf-8"))

if "error" in data:
    print("API Error:", data["error"])
else:
    content = data["choices"][0]["message"]["content"]

    print("\nLLM Response:")
    print(content)

    # 解析JSON
    try:
        courses = json.loads(content)
    except:
        match = re.search(r'\[.*\]', content, re.DOTALL)
        if match:
            courses = json.loads(match.group())
        else:
            print("Failed to parse JSON")
            exit(1)

    print("\n" + "=" * 60)
    print("解析结果:")
    print("=" * 60)
    print("课程数量: %d" % len(courses))

    day_names = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"]
    for i, c in enumerate(courses, 1):
        name = c.get("name", "?")
        day = day_names[c.get("day", 0)]
        start = c.get("startPeriod", "?")
        end = c.get("endPeriod", "?")
        teacher = c.get("teacher", "?")
        location = c.get("location", "?")
        weeks = c.get("weeks", [])
        ctype = c.get("type", "?")

        if weeks:
            week_str = "%d-%d周" % (min(weeks), max(weeks))
        else:
            week_str = "?"

        print("%d. %s" % (i, name))
        print("   时间: %s 第%d-%d节" % (day, start, end))
        print("   教师: %s | 地点: %s" % (teacher, location))
        print("   周次: %s (%s)" % (week_str, ctype))
        print()
