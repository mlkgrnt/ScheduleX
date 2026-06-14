import base64
import json
import requests
from PIL import Image
import io

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

# Try different approaches
# Approach 1: Try with the exact model name and a simpler prompt
# Approach 2: Try without JSON constraint
# Approach 3: Try with different content format

# Let's first try listing available models or try variations
models_to_try = [
    "deepseek-ai/DeepSeek-OCR",
    "Pro/deepseek-ai/DeepSeek-OCR",
]

prompts_to_try = [
    "OCR this image and extract all text.",
    "请识别图片中的所有文字内容。",
]

for model in models_to_try:
    for prompt in prompts_to_try:
        print(f"\n{'='*60}")
        print(f"Model: {model}")
        print(f"Prompt: {prompt}")
        
        payload = {
            "model": model,
            "messages": [{
                "role": "user",
                "content": [
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_str}"}},
                    {"type": "text", "text": prompt}
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
                    print(f"Content: {content[:500]}")
                else:
                    print("Content is EMPTY")
                    # Check for errors in response
                    if "error" in resp_json:
                        print(f"Error: {resp_json['error']}")
            else:
                print(f"Error response: {resp.text[:500]}")
        except Exception as e:
            print(f"Exception: {e}")
