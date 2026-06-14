import base64
import json
import requests
from PIL import Image
import io
import re

# Load and prepare image
img_path = r"C:\Users\Ashley Wilkes\AppData\Local\hermes\image_cache\img_c7eefa6d25b7.jpg"
img = Image.open(img_path)
print(f"Original size: {img.size}")

max_dim = 1600
ratio = min(max_dim / img.width, max_dim / img.height)
if ratio < 1:
    new_size = (int(img.width * ratio), int(img.height * ratio))
    img = img.resize(new_size, Image.LANCZOS)
    print(f"Resized to: {img.size}")

buf = io.BytesIO()
img = img.convert("RGB")
img.save(buf, format="JPEG", quality=80)
jpeg_bytes = buf.getvalue()
b64_str = base64.b64encode(jpeg_bytes).decode("utf-8")
print(f"Base64 length: {len(b64_str)}")

api_key = "sk-ekrjwsgawxrbvatlmheqsshiaphuavfqaigysbvjndbaaihp"
url = "https://api.siliconflow.cn/v1/chat/completions"

prompts = [
    {
        "name": "Original JSON prompt (from task)",
        "text": "请识别这张课表图片中的所有课程信息，包括课程名称、教师、地点、星期几、节次、周次。以JSON数组格式输出。"
    },
    {
        "name": "Structured table extraction",
        "text": "This is a class schedule. Please read the table structure carefully. For each cell, identify: day of week (column), time slot (row), course name, teacher, location, and week range. Output a JSON array where each element has keys: day, slot, course, teacher, location, weeks. Output ONLY the JSON array."
    },
    {
        "name": "Markdown table output",
        "text": "This is a class schedule image. Please extract the table as a markdown table with columns: Time Slot, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday."
    }
]

for p in prompts:
    print(f"\n{'='*70}")
    print(f"PROMPT: {p['name']}")
    print(f"{'='*70}")
    
    payload = {
        "model": "deepseek-ai/DeepSeek-OCR",
        "messages": [{
            "role": "user",
            "content": [
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_str}"}},
                {"type": "text", "text": p['text']}
            ]
        }],
        "max_tokens": 4096,
        "temperature": 0.1
    }
    
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}"
    }
    
    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=120)
        print(f"HTTP Status: {resp.status_code}")
        
        if resp.status_code == 200:
            resp_json = resp.json()
            content = resp_json.get("choices", [{}])[0].get("message", {}).get("content", "")
            usage = resp_json.get("usage", {})
            print(f"Usage: {usage}")
            print(f"Content length: {len(content)}")
            
            if content:
                print(f"\n--- CONTENT ---")
                print(content)
                
                # Try JSON parse
                print(f"\n--- JSON VALIDATION ---")
                try:
                    parsed = json.loads(content)
                    if isinstance(parsed, list):
                        print(f"VALID JSON ARRAY with {len(parsed)} items")
                        for item in parsed:
                            print(f"  {json.dumps(item, ensure_ascii=False)}")
                    else:
                        print(f"VALID JSON but type is {type(parsed).__name__}")
                except json.JSONDecodeError as e:
                    print(f"Not pure JSON: {e}")
                    json_match = re.search(r'```(?:json)?\s*\n(.*?)\n```', content, re.DOTALL)
                    if json_match:
                        try:
                            parsed = json.loads(json_match.group(1))
                            if isinstance(parsed, list):
                                print(f"Found JSON in code block: {len(parsed)} items")
                                for item in parsed:
                                    print(f"  {json.dumps(item, ensure_ascii=False)}")
                        except json.JSONDecodeError as e2:
                            print(f"JSON in code block also invalid: {e2}")
                    else:
                        print("No JSON code block found either")
            else:
                print("Content is EMPTY")
        else:
            print(f"Error: {resp.text[:300]}")
    except Exception as e:
        print(f"Exception: {e}")
