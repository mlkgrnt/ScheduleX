#!/usr/bin/env python3
"""
端到端测试：模拟强智教务系统的真实HTML结构，测试整个提取+LLM解析管线。
包括：
1. 主页面+iframe结构（强智系统典型布局）
2. HtmlExtractor的JS提取逻辑模拟
3. LLM解析
"""
import json
import urllib.request
import ssl
import sys
import re

API_BASE = "https://token-plan-cn.xiaomimimo.com/v1"
API_KEY = sys.argv[1] if len(sys.argv) > 1 else ""
MODEL = "mimo-v2.5-pro"

# ============================================================
# 模拟强智教务系统的真实HTML结构
# 强智系统课表通常在iframe内的kbtable中
# ============================================================

# 主页面（包含iframe）
MAIN_PAGE_HTML = """
<html>
<head><title>正方教务系统 - 学生个人课表</title></head>
<body>
<div id="header">
  <h1>汕头大学教务管理系统</h1>
  <div class="nav">
    <a href="/jsxsd/xskb/xskb_list.do">我的课表</a>
    <a href="/jsxsd/kbcx/kbcx_list.do">课程查询</a>
  </div>
</div>
<div id="content">
  <iframe id="mainFrame" name="mainFrame" src="/jsxsd/xskb/xskb_list.do" 
          style="width:100%;height:800px;border:none;"></iframe>
</div>
</body>
</html>
"""

# iframe内的课表页面（强智系统真实结构）
COURSE_TABLE_HTML = """
<html>
<head><title>学生个人课表</title></head>
<body>
<div id="kbtable">
<table id="kbcontent" width="100%" border="1" cellpadding="0" cellspacing="0">
  <thead>
    <tr>
      <th>节次</th>
      <th>星期一</th>
      <th>星期二</th>
      <th>星期三</th>
      <th>星期四</th>
      <th>星期五</th>
      <th>星期六</th>
      <th>星期日</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>上午第1,2节</td>
      <td>
        <div class="kbcontent">
          <font title="课程名">高等数学A(二)</font><br/>
          <font title="老师">陈晓明</font><br/>
          <font title="教室">教学楼A301</font><br/>
          [1-16周]
        </div>
      </td>
      <td></td>
      <td>
        <div class="kbcontent">
          <font title="课程名">大学物理(上)</font><br/>
          <font title="老师">李伟华</font><br/>
          <font title="教室">理学院B205</font><br/>
          [1-16周]
        </div>
      </td>
      <td></td>
      <td>
        <div class="kbcontent">
          <font title="课程名">电路分析基础</font><br/>
          <font title="老师">王建国</font><br/>
          <font title="教室">工学院C102</font><br/>
          [1-8周]
        </div>
      </td>
      <td></td>
      <td></td>
    </tr>
    <tr>
      <td>上午第3,4节</td>
      <td></td>
      <td>
        <div class="kbcontent">
          <font title="课程名">线性代数</font><br/>
          <font title="老师">赵丽萍</font><br/>
          <font title="教室">教学楼A201</font><br/>
          [1-16周]
        </div>
      </td>
      <td></td>
      <td>
        <div class="kbcontent">
          <font title="课程名">程序设计基础</font><br/>
          <font title="老师">刘志强</font><br/>
          <font title="教室">计算机楼D301</font><br/>
          [1-16周]
        </div>
      </td>
      <td>
        <div class="kbcontent">
          <font title="课程名">体育(二)</font><br/>
          <font title="老师">张强</font><br/>
          <font title="教室">体育馆</font><br/>
          [1-16周 单周]
        </div>
      </td>
      <td></td>
      <td></td>
    </tr>
    <tr>
      <td>下午第5,6节</td>
      <td>
        <div class="kbcontent">
          <font title="课程名">中国近现代史纲要</font><br/>
          <font title="老师">孙明</font><br/>
          <font title="教室">文科楼E201</font><br/>
          [1-16周]
        </div>
      </td>
      <td></td>
      <td>
        <div class="kbcontent">
          <font title="课程名">英语听说(二)</font><br/>
          <font title="老师">Smith John</font><br/>
          <font title="教室">外语楼F102</font><br/>
          [2-16周 双周]
        </div>
      </td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
    </tr>
  </tbody>
</table>
</div>
</body>
</html>
"""

# ============================================================
# 模拟 HtmlExtractor 的 findSchedule 逻辑
# ============================================================
def simulate_js_extraction(main_html, iframe_html):
    """模拟JS提取逻辑"""
    results = []
    
    # 1. 检查主页面的已知选择器
    selectors = ['#kbtable', '#kbcontent', '#courseTable', '#mytable',
                 '.kbtable', '.kbcontent', '.courseTable',
                 '#mainTable', '#table1', '#DataGrid1',
                 '#kbdiv', '.kbdiv', '#xskbtable']
    
    for sel in selectors:
        if sel.startswith('#'):
            tag_id = sel[1:]
            if f'id="{tag_id}"' in main_html:
                results.append(f"主页面找到 {sel}")
        elif sel.startswith('.'):
            tag_class = sel[1:]
            if f'class="{tag_class}"' in main_html:
                results.append(f"主页面找到 {sel}")
    
    # 2. 检查iframe
    if '<iframe' in main_html:
        results.append(f"发现iframe")
        # 模拟访问iframe内容
        for sel in selectors:
            if sel.startswith('#'):
                tag_id = sel[1:]
                if f'id="{tag_id}"' in iframe_html:
                    results.append(f"iframe内找到 {sel}")
                    # 提取表格内容
                    start = iframe_html.find(f'id="{tag_id}"')
                    if start != -1:
                        # 找到包含这个id的table/div
                        table_start = iframe_html.rfind('<table', 0, start)
                        if table_start == -1:
                            table_start = iframe_html.rfind('<div', 0, start)
                        if table_start != -1:
                            return iframe_html[table_start:table_start+5000], results
    
    # 3. 降级：搜索含有课程关键词的表格
    import re
    tables = re.findall(r'<table[^>]*>.*?</table>', iframe_html, re.DOTALL)
    for t in tables:
        if '课程' in t or '节' in t or '周' in t:
            results.append(f"通过关键词找到表格")
            return t[:5000], results
    
    return None, results

# ============================================================
# LLM解析
# ============================================================
SYSTEM_PROMPT = """你是一个课程表解析助手。用户会给你一个教务系统课表页面的 HTML 片段。
请从中提取所有课程信息，返回一个 JSON 数组。

输出格式（严格遵循，不要添加任何其他文本）：
[{"name":"课程名称","teacher":"教师姓名","location":"上课地点","day":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2,3],"type":"all"}]

注意：
- day 取值：周一=1, 周二=2, ..., 周日=7
- type: "all"=每周, "odd"=单周, "even"=双周
- 如果周次是连续的（如 1-16 周），展开为数组
- 只输出 JSON 数组，不要输出其他任何内容"""

def call_llm(html_content):
    """调用LLM解析课表"""
    request_body = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": "请解析以下课表 HTML：\n\n" + html_content}
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
    
    resp = urllib.request.urlopen(req, context=ctx, timeout=120)
    data = json.loads(resp.read().decode('utf-8'))
    
    if 'error' in data:
        raise Exception(f"API Error: {data['error']}")
    
    content = data['choices'][0]['message']['content']
    reasoning = data['choices'][0]['message'].get('reasoning_content', '')
    
    return content, reasoning

# ============================================================
# 运行测试
# ============================================================
print("=" * 60)
print("ScheduleX 端到端课表提取测试")
print("=" * 60)

print("\n[1] 模拟HtmlExtractor提取...")
print(f"主页面大小: {len(MAIN_PAGE_HTML)} chars")
print(f"iframe课表页大小: {len(COURSE_TABLE_HTML)} chars")

extracted_html, diag = simulate_js_extraction(MAIN_PAGE_HTML, COURSE_TABLE_HTML)
print(f"\n提取结果: {'成功' if extracted_html else '失败'}")
print(f"诊断信息:")
for d in diag:
    print(f"  ✓ {d}")

if not extracted_html:
    print("\n❌ 提取失败，无法继续")
    sys.exit(1)

print(f"\n提取的HTML大小: {len(extracted_html)} chars")
print(f"HTML前200字符: {extracted_html[:200]}...")

print("\n[2] 调用LLM解析...")
try:
    content, reasoning = call_llm(extracted_html)
    
    if reasoning:
        print(f"\nLLM推理过程:")
        print(reasoning[:300] + "..." if len(reasoning) > 300 else reasoning)
    
    print(f"\nLLM原始响应:")
    print(content)
    
    print("\n[3] 解析JSON...")
    # 尝试直接解析
    try:
        courses = json.loads(content)
    except json.JSONDecodeError:
        # 尝试从响应中提取JSON数组
        match = re.search(r'\[.*\]', content, re.DOTALL)
        if match:
            courses = json.loads(match.group())
        else:
            raise Exception("无法从LLM响应中提取JSON")
    
    print(f"\n✅ 成功解析 {len(courses)} 门课程:")
    print("-" * 60)
    
    day_names = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
    for i, c in enumerate(courses, 1):
        name = c.get('name', '?')
        day = day_names[c.get('day', 0)]
        start = c.get('startPeriod', '?')
        end = c.get('endPeriod', '?')
        teacher = c.get('teacher', '?')
        location = c.get('location', '?')
        weeks = c.get('weeks', [])
        ctype = c.get('type', '?')
        
        week_str = f"{weeks[0]}-{weeks[-1]}周" if weeks else "?"
        print(f"{i}. {name}")
        print(f"   时间: {day} 第{start}-{end}节")
        print(f"   教师: {teacher} | 地点: {location}")
        print(f"   周次: {week_str} ({ctype})")
        print()
    
    # 验证
    print("=" * 60)
    print("验证结果:")
    expected = [
        ("高等数学A(二)", 1, 1, 2, "all"),      # 周一 1-2节
        ("大学物理(上)", 3, 1, 2, "all"),        # 周三 1-2节
        ("电路分析基础", 5, 1, 2, "all"),        # 周五 1-2节 (1-8周)
        ("线性代数", 2, 3, 4, "all"),            # 周二 3-4节
        ("程序设计基础", 4, 3, 4, "all"),        # 周四 3-4节
        ("体育(二)", 5, 3, 4, "odd"),            # 周五 3-4节 单周
        ("中国近现代史纲要", 1, 5, 6, "all"),    # 周一 5-6节
        ("英语听说(二)", 3, 5, 6, "even"),       # 周三 5-6节 双周
    ]
    
    passed = 0
    for exp_name, exp_day, exp_start, exp_end, exp_type in expected:
        found = False
        for c in courses:
            if (exp_name in c.get('name', '') and 
                c.get('day') == exp_day and
                c.get('startPeriod') == exp_start and
                c.get('endPeriod') == exp_end):
                # 检查type
                if exp_type == "all" and c.get('type') == "all":
                    found = True
                    break
                elif exp_type == "odd" and c.get('type') == "odd":
                    found = True
                    break
                elif exp_type == "even" and c.get('type') == "even":
                    found = True
                    break
        
        status = "✅" if found else "❌"
        if found:
            passed += 1
        print(f"  {status} {exp_name} ({day_names[exp_day]} {exp_start}-{exp_end}节, {exp_type})")
    
    print(f"\n通过: {passed}/{len(expected)}")
    
    if passed == len(expected):
        print("\n🎉 全部测试通过！课表提取管线工作正常。")
    else:
        print(f"\n⚠️ 有 {len(expected)-passed} 项未通过，需要检查。")
        
except Exception as e:
    print(f"\n❌ 错误: {e}")
    import traceback
    traceback.print_exc()
