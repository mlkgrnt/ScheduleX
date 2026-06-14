import base64
import json
import requests
from PIL import Image
import io
import sys

# Step 1: Resize and compress image
img_path = r"C:\Users\Ashley Wilkes\AppData\Local\hermes\image_cache\img_c7eefa6d25b7.jpg"
print(f"Loading image: {img_path}")
img = Image.open(img_path)
print(f"Original size: {img.size}")

# Resize to max 1600px
max_dim = 1600
ratio = min(max_dim / img.width, max_dim / img.height)
if ratio < 1:
    new_size = (int(img.width * ratio), int(img.height * ratio))
    img = img.resize(new_size, Image.LANCZOS)
    print(f"Resized to: {img.size}")
else:
    print("No resize needed")

# Compress to JPEG quality=80
buf = io.BytesIO()
img = img.convert("RGB")
img.save(buf, format="JPEG", quality=80)
jpeg_bytes = buf.getvalue()
print(f"JPEG size: {len(jpeg_bytes)} bytes")

# Base64 encode
b64_str = base64.b64encode(jpeg_bytes).decode("utf-8")
print(f"Base64 length: {len(b64_str)}")

# Step 2: Call API
api_key = "sk-ekrjwsgawxrbvatlmheqsshiaphuavfqaigysbvjndbaaihp"
url = "https://api.siliconflow.cn/v1/chat/completions"

payload = {
    "model": "deepseek-ai/DeepSeek-OCR",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_str}"}},
            {"type": "text", "text": "请识别这张课表图片中的所有课程信息，包括课程名称、教师、地点、星期几、节次、周次。以JSON数组格式输出。"}
        ]
    }],
    "max_tokens": 4096,
    "temperature": 0.1
}

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {api_key}"
}

print("\nCalling SiliconFlow DeepSeek-OCR API...")
print(f"Model: {payload['model']}")

try:
    resp = requests.post(url, headers=headers, json=payload, timeout=120)
    print(f"\nHTTP Status Code: {resp.status_code}")
    print(f"Response Headers: {dict(resp.headers)}")
    
    print("\n--- FULL RESPONSE ---")
    try:
        resp_json = resp.json()
        print(json.dumps(resp_json, ensure_ascii=False, indent=2))
        
        # Extract the content
        if "choices" in resp_json and len(resp_json["choices"]) > 0:
            content = resp_json["choices"][0].get("message", {}).get("content", "")
            print("\n--- MODEL OUTPUT TEXT ---")
            print(content)
            
            # Check if it's valid JSON
            print("\n--- JSON VALIDATION ---")
            try:
                parsed = json.loads(content)
                if isinstance(parsed, list):
                    print(f"VALID JSON ARRAY with {len(parsed)} items")
                    for item in parsed:
                        print(f"  - {json.dumps(item, ensure_ascii=False)}")
                else:
                    print(f"VALID JSON but type is {type(parsed).__name__}, not array")
            except json.JSONDecodeError as e:
                print(f"NOT valid JSON: {e}")
                print("The model output raw text instead of JSON format.")
        else:
            print("No 'choices' in response or empty choices")
    except json.JSONDecodeError:
        print(resp.text)
        
except Exception as e:
    print(f"Error: {e}")
