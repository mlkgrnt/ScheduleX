import json
import urllib.request
import ssl
import sys
import re

API_BASE = "https://token-plan-cn.xiaomimimo.com/v1"
API_KEY = sys.argv[1] if len(sys.argv) > 1 else ""
MODEL = "mimo-v2.5-pro"

MOCK_HTML = '<div id="kbtable"><table><tr><td>节次</td><td>周一</td><td>周二</td><td>周三</td><td>周四</td><td>周五</td></tr><tr><td>第1,2节</td><td><div class="kbcontent"><font title="课程名">高等数学A</font><br><font title="老师">张老师</font><br><font title="教室">A301</font><br>[1-16周]</div></td><td></td><td><div class="kbcontent"><font title="课程名">大学英语</font><br><font title="老师">李老师</font><br><font title="教室">B205</font><br>[1-16周]</div></td><td></td><td><div class="kbcontent"><font title="课程名">计算机基础</font><br><font title="老师">王老师</font><br><font title="教室">C102</font><br>[1-8周]</div></td></tr><tr><td>第3,4节</td><td></td><td><div class="kbcontent"><font title="课程名">线性代数</font><br><font title="老师">赵老师</font><br><font title="教室">A201</font><br>[1-16周]</div></td><td></td><td><div class="kbcontent"><font title="课程名">体育</font><br><font title="老师">刘老师</font><br><font title="教室">体育馆</font><br>[1-16周 单周]</div></td><td></td></tr></table></div>'

SYSTEM_PROMPT = """你是一个课程表解析助手。用户会给你一个教务系统课表页面的 HTML 片段。
请从中提取所有课程信息，返回一个 JSON 数组。

输出格式（严格遵循，不要添加任何其他文本）：
[{"name":"课程名称","teacher":"教师姓名","location":"上课地点","day":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2,3],"type":"all"}]

注意：
- day 取值：周一=1, 周二=2, ..., 周日=7
- type: "all"=每周, "odd"=单周, "even"=双周
- 如果周次是连续的（如 1-16 周），展开为数组
- 只输出 JSON 数组，不要输出其他任何内容"""

request_body = json.dumps({
    "model": MODEL,
    "messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "请解析以下课表 HTML：\n\n" + MOCK_HTML}
    ],
    "max_tokens": 4096
}, ensure_ascii=False).encode('utf-8')

ctx = ssl.create_default_context()
req = urllib.request.Request(
    API_BASE + "/chat/completions",
    data=request_body,
    headers={
        "Content-Type": "application/json",
        "Authorization": "Bearer " + API_KEY
    }
)

print("Sending request to LLM API...")
try:
    resp = urllib.request.urlopen(req, context=ctx, timeout=120)
    data = json.loads(resp.read().decode('utf-8'))

    if 'error' in data:
        print("API Error:", data['error'])
    else:
        content = data['choices'][0]['message']['content']
        reasoning = data['choices'][0]['message'].get('reasoning_content', '')
        if reasoning:
            print("Reasoning:", reasoning[:200])
        print("LLM Response:")
        print(content)
        print("\n--- Parsing ---")
        try:
            courses = json.loads(content)
            print("SUCCESS:", len(courses), "courses parsed")
            day_names = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
            for c in courses:
                print("  " + c['name'] + " | " + day_names[c['day']] + " | " + str(c['startPeriod']) + "-" + str(c['endPeriod']) + "节 | " + str(c.get('teacher','')) + " | " + str(c.get('location','')) + " | " + c['type'])
        except json.JSONDecodeError as e:
            print("JSON parse failed:", e)
            match = re.search(r'\[.*\]', content, re.DOTALL)
            if match:
                courses = json.loads(match.group())
                print("Recovered:", len(courses), "courses")
except Exception as e:
    print("Error:", e)
