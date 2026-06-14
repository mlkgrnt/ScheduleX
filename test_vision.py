import base64
import json
import requests
import io
from PIL import Image

# Read image and encode to base64
image_path = r"C:\Users\Ashley Wilkes\AppData\Local\hermes\image_cache\img_c7eefa6d25b7.jpg"
with open(image_path, "rb") as f:
    image_data = f.read()

print(f"Image size: {len(image_data)} bytes")

# Resize to max 1600px
img = Image.open(io.BytesIO(image_data))
print(f"Original size: {img.size}")

max_dim = 1600
ratio = min(max_dim / img.width, max_dim / img.height, 1.0)
if ratio < 1:
    new_size = (int(img.width * ratio), int(img.height * ratio))
    img = img.resize(new_size, Image.LANCZOS)
    print(f"Resized to: {img.size}")

# Compress to JPEG
buffer = io.BytesIO()
img.save(buffer, format="JPEG", quality=85)
jpeg_data = buffer.getvalue()
print(f"JPEG size: {len(jpeg_data)} bytes")

image_base64 = base64.b64encode(jpeg_data).decode("utf-8")
print(f"Base64 length: {len(image_base64)}")

# API call
url = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"
api_key = "tp-c09szrbj88ope330ziyqf73ou0jp11r4wvsamelok9xi809o"
model = "mimo-v2-omni"

prompt = """你是一个课程表解析助手。用户会给你一张课表截图，请从中提取所有课程信息，返回一个 JSON 数组。

课表结构说明：
- 这是一个7列×5行的表格
- 列（从左到右）：星期一(1)、星期二(2)、星期三(3)、星期四(4)、星期五(5)、星期六(6)、星期日(7)
- 行（从上到下）：
  - 第一大节 = 小节1,2
  - 第二大节 = 小节3,4,5
  - 第三大节 = 小节6,7
  - 第四大节 = 小节8,9,10
  - 第五大节 = 小节11,12,13

输出格式（严格遵循）：
[
  {
    "name": "课程名称",
    "teacher": "教师姓名",
    "location": "上课地点",
    "day": 1,
    "startPeriod": 1,
    "endPeriod": 2,
    "weeks": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],
    "type": "all"
  }
]

重要规则：
- day 必须是数字1-7，根据课程所在的列确定（星期一=1到星期日=7）。绝对不能为null！
- startPeriod/endPeriod：使用小节号（如1,2,3...13），根据课程所在的行确定
- type: "all"=每周, "odd"=单周, "even"=双周
- 周次格式如"1-16周"展开为[1,2,...,16]，"9,11,13,15周"展开为[9,11,13,15]
- 如果某课程在多个时间段上课，每个时间段单独一条记录
- 只输出 JSON 数组，不要输出其他任何内容"""

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {api_key}"
}

payload = {
    "model": model,
    "temperature": 0.1,
    "max_tokens": 16384,
    "messages": [
        {
            "role": "system",
            "content": prompt
        },
        {
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": "请解析这张课表截图中的所有课程信息。"
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:image/jpeg;base64,{image_base64}"
                    }
                }
            ]
        }
    ]
}

print("\nSending request...")
print(f"Model: {model}")
print(f"Payload size: {len(json.dumps(payload))} bytes")

try:
    response = requests.post(url, headers=headers, json=payload, timeout=180)
    print(f"Status code: {response.status_code}")
    result = response.json()
    
    # Check for content or reasoning_content
    message = result.get("choices", [{}])[0].get("message", {})
    content = message.get("content", "")
    reasoning = message.get("reasoning_content", "")
    
    actual_content = content if content.strip() else reasoning
    
    print(f"\nContent length: {len(content)}")
    print(f"Reasoning length: {len(reasoning)}")
    print(f"\nActual response:\n{actual_content[:3000]}")
    
    # Try to parse JSON
    if actual_content:
        try:
            # Find JSON array
            start = actual_content.find("[")
            end = actual_content.rfind("]") + 1
            if start >= 0 and end > start:
                json_str = actual_content[start:end]
                courses = json.loads(json_str)
                print(f"\n=== Parsed {len(courses)} courses ===")
                for c in courses:
                    print(f"  {c.get('name')}: day={c.get('day')}, period={c.get('startPeriod')}-{c.get('endPeriod')}, weeks={c.get('weeks')}")
        except json.JSONDecodeError as e:
            print(f"\nJSON parse error: {e}")
except Exception as e:
    print(f"Error: {e}")
