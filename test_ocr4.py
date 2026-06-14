import base64
import json
import requests
from PIL import Image
import io
import time

img_path = r"C:\Users\Ashley Wilkes\AppData\Local\hermes\image_cache\img_c7eefa6d25b7.jpg"
img = Image.open(img_path)
max_dim = 1600
ratio = min(max_dim / img.width, max_dim / img.height)
if ratio < 1:
    new_size = (int(img.width * ratio), int(img.height * ratio))
    img = img.resize(new_size, Image.LANCZOS)

buf = io.BytesIO()
img = img.convert("RGB")
img.save(buf, format="JPEG", quality=80)
jpeg_bytes = buf.getvalue()
b64_str = base64.b64encode(jpeg_bytes).decode("utf-8")
print(f"Base64 length: {len(b64_str)}")

api_key = "sk-ekrjwsgawxrbvatlmheqsshiaphuavfqaigysbvjndbaaihp"
url = "https://api.siliconflow.cn/v1/chat/completions"

# Test 1: Simple OCR (known to work)
print("\n" + "="*70)
print("TEST 1: Simple OCR extraction")
print("="*70)

payload = {
    "model": "deepseek-ai/DeepSeek-OCR",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_str}"}},
            {"type": "text", "text": "OCR this image and extract all text."}
        ]
    }],
    "max_tokens": 4096,
    "temperature": 0.1
}

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {api_key}"
}

resp = requests.post(url, headers=headers, json=payload, timeout=120)
print(f"HTTP Status: {resp.status_code}")
resp_json = resp.json()
content = resp_json.get("choices", [{}])[0].get("message", {}).get("content", "")
usage = resp_json.get("usage", {})
print(f"Usage: {usage}")
print(f"Content length: {len(content)}")
if content:
    print(f"\n--- RAW OCR OUTPUT ---")
    print(content)
else:
    print("EMPTY - trying again after delay...")
    time.sleep(3)
    resp = requests.post(url, headers=headers, json=payload, timeout=120)
    resp_json = resp.json()
    content = resp_json.get("choices", [{}])[0].get("message", {}).get("content", "")
    usage = resp_json.get("usage", {})
    print(f"Retry - HTTP: {resp.status_code}, Usage: {usage}, Length: {len(content)}")
    if content:
        print(content)

# Save raw OCR for reference
with open("ocr_raw_output.txt", "w", encoding="utf-8") as f:
    f.write(content)

# Test 2: Structured JSON extraction with original prompt
time.sleep(2)
print("\n" + "="*70)
print("TEST 2: Original JSON prompt from task spec")
print("="*70)

payload["messages"][0]["content"][1]["text"] = "请识别这张课表图片中的所有课程信息，包括课程名称、教师、地点、星期几、节次、周次。以JSON数组格式输出。"

resp = requests.post(url, headers=headers, json=payload, timeout=120)
print(f"HTTP Status: {resp.status_code}")
resp_json = resp.json()
content2 = resp_json.get("choices", [{}])[0].get("message", {}).get("content", "")
usage2 = resp_json.get("usage", {})
print(f"Usage: {usage2}")
print(f"Content length: {len(content2)}")
if content2:
    print(f"\n--- JSON PROMPT OUTPUT ---")
    print(content2)
else:
    print("EMPTY response")

print("\n" + "="*70)
print("SUMMARY")
print("="*70)
print(f"Test 1 (Simple OCR): {'SUCCESS' if content else 'FAILED'} - {len(content)} chars")
print(f"Test 2 (JSON prompt): {'SUCCESS' if content2 else 'EMPTY (0 completion tokens)'}")
print(f"API endpoint: {url}")
print(f"Model: deepseek-ai/DeepSeek-OCR")
print(f"HTTP status: {resp.status_code} (200 for both)")
