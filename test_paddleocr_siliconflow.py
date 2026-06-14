"""
SiliconFlow API Test for PaddleOCR-VL-1.5
==========================================

API Endpoint: https://api.siliconflow.cn/v1/chat/completions
Model ID: PaddlePaddle/PaddleOCR-VL-1.5

This script tests the SiliconFlow vision API using the standard OpenAI-compatible format.
The PaddleOCR-VL-1.5 model is a vision-language model specialized for OCR tasks.

API Format (OpenAI-compatible):
{
  "model": "PaddlePaddle/PaddleOCR-VL-1.5",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/jpeg;base64,<BASE64_DATA>"
          }
        },
        {
          "type": "text",
          "text": "<prompt>"
        }
      ]
    }
  ],
  "max_tokens": 4096,
  "temperature": 0.1
}

Usage:
  python test_paddleocr_siliconflow.py --api-key sk-xxxxx
  python test_paddleocr_siliconflow.py --api-key sk-xxxxx --save-json
"""

import base64
import json
import sys
import argparse
import requests
from pathlib import Path

# === Configuration ===
API_URL = "https://api.siliconflow.cn/v1/chat/completions"
MODEL = "PaddlePaddle/PaddleOCR-VL-1.5"
IMAGE_PATH = r"C:\Users\Ashley Wilkes\AppData\Local\hermes\image_cache\img_c7eefa6d25b7.jpg"

PROMPT = "请识别这张课表图片中的所有课程信息，包括课程名称、教师、地点、星期几、节次、周次。以JSON数组格式输出。"

DETAILED_PROMPT = """你是一个课程表解析助手。请从这张课表截图中提取所有课程信息，返回一个JSON数组。

课表结构说明：
- 这是一个7列×N行的表格
- 列（从左到右）：星期一(1)、星期二(2)、星期三(3)、星期四(4)、星期五(5)、星期六(6)、星期日(7)
- 行：从上到下对应不同的节次

输出格式（严格遵循）：
[{"name":"课程名称","teacher":"教师姓名","location":"上课地点","day":1,"startPeriod":1,"endPeriod":2,"weeks":[1,2,3],"type":"all"}]

重要规则：
- day: 数字1-7（周一=1到周日=7）
- startPeriod/endPeriod: 小节号
- type: "all"=每周, "odd"=单周, "even"=双周
- 周次展开为数组，如"1-16周"→[1,2,...,16]
- 多个时间段每个单独一条记录
- 只输出JSON数组，不要其他内容"""


def encode_image(image_path: str, max_size: int = 1600, quality: int = 85) -> str:
    """Read and compress image, return base64 string."""
    try:
        from PIL import Image
        import io
        
        with open(image_path, "rb") as f:
            image_data = f.read()
        
        img = Image.open(io.BytesIO(image_data))
        print(f"Original image: {img.size[0]}x{img.size[1]}, {len(image_data)} bytes")
        
        # Resize if needed
        ratio = min(max_size / img.width, max_size / img.height, 1.0)
        if ratio < 1:
            new_size = (int(img.width * ratio), int(img.height * ratio))
            img = img.resize(new_size, Image.LANCZOS)
            print(f"Resized to: {img.size[0]}x{img.size[1]}")
        
        # Compress to JPEG
        buffer = io.BytesIO()
        img.save(buffer, format="JPEG", quality=quality)
        jpeg_data = buffer.getvalue()
        print(f"JPEG compressed: {len(jpeg_data)} bytes")
        
        return base64.b64encode(jpeg_data).decode("utf-8")
    except ImportError:
        print("PIL not available, using raw image data")
        with open(image_path, "rb") as f:
            return base64.b64encode(f.read()).decode("utf-8")


def build_request(image_base64: str, prompt: str, detailed: bool = False) -> dict:
    """Build the OpenAI-compatible vision request payload."""
    actual_prompt = DETAILED_PROMPT if detailed else PROMPT
    return {
        "model": MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{image_base64}"
                        }
                    },
                    {
                        "type": "text",
                        "text": actual_prompt
                    }
                ]
            }
        ],
        "max_tokens": 4096,
        "temperature": 0.1
    }


def call_api(api_key: str, payload: dict) -> dict:
    """Call the SiliconFlow API."""
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}"
    }
    
    print(f"\n{'='*60}")
    print(f"API Endpoint: {API_URL}")
    print(f"Model: {payload['model']}")
    print(f"Payload size: {len(json.dumps(payload)):,} bytes")
    print(f"{'='*60}\n")
    
    response = requests.post(API_URL, headers=headers, json=payload, timeout=120)
    print(f"HTTP Status: {response.status_code}")
    
    return response.json()


def parse_courses(response_text: str) -> list:
    """Try to extract JSON array from model response."""
    try:
        # Try direct JSON parse
        return json.loads(response_text)
    except json.JSONDecodeError:
        # Try to find JSON array in text
        start = response_text.find("[")
        end = response_text.rfind("]") + 1
        if start >= 0 and end > start:
            try:
                return json.loads(response_text[start:end])
            except json.JSONDecodeError:
                pass
    return []


def main():
    parser = argparse.ArgumentParser(description="Test SiliconFlow PaddleOCR-VL-1.5 API")
    parser.add_argument("--api-key", "-k", help="SiliconFlow API key (sk-...)")
    parser.add_argument("--save-json", "-s", action="store_true", help="Save request JSON to file")
    parser.add_argument("--detailed", "-d", action="store_true", help="Use detailed prompt")
    parser.add_argument("--image", "-i", help="Override image path")
    args = parser.parse_args()
    
    image_path = args.image or IMAGE_PATH
    
    # Encode image
    print(f"Image: {image_path}")
    image_base64 = encode_image(image_path)
    print(f"Base64 length: {len(image_base64):,} chars")
    
    # Build request
    payload = build_request(image_base64, PROMPT, detailed=args.detailed)
    
    # Save request JSON
    if args.save_json or not args.api_key:
        output_path = Path(__file__).parent / "paddleocr_vl_request.json"
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"\nRequest JSON saved: {output_path}")
        print(f"File size: {output_path.stat().st_size:,} bytes")
    
    # Save template (without base64)
    template_path = Path(__file__).parent / "paddleocr_vl_format.json"
    template = json.loads(json.dumps(payload))
    template["messages"][0]["content"][0]["image_url"]["url"] = "data:image/jpeg;base64,<BASE64>"
    with open(template_path, "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
    print(f"Format template saved: {template_path}")
    
    # Call API if key provided
    if args.api_key:
        result = call_api(args.api_key, payload)
        
        print(f"\nAPI Response:")
        print(json.dumps(result, ensure_ascii=False, indent=2)[:2000])
        
        if "choices" in result:
            msg = result["choices"][0].get("message", {})
            content = msg.get("content", "")
            reasoning = msg.get("reasoning_content", "")
            actual = content if content.strip() else reasoning
            
            print(f"\n{'='*60}")
            print(f"Model Response ({len(actual)} chars):")
            print(f"{'='*60}")
            print(actual[:3000])
            
            courses = parse_courses(actual)
            if courses:
                print(f"\n{'='*60}")
                print(f"Parsed {len(courses)} courses:")
                print(f"{'='*60}")
                day_names = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"]
                for i, c in enumerate(courses, 1):
                    name = c.get("name", "?")
                    day = day_names[c.get("day", 0)] if c.get("day") else "?"
                    start = c.get("startPeriod", "?")
                    end = c.get("endPeriod", "?")
                    teacher = c.get("teacher", "?")
                    location = c.get("location", "?")
                    weeks = c.get("weeks", [])
                    print(f"  {i}. {name}")
                    print(f"     {day} 第{start}-{end}节 | {teacher} | {location}")
                    if weeks:
                        print(f"     周次: {min(weeks)}-{max(weeks)}周 ({len(weeks)}周)")
                    print()
        elif "error" in result:
            print(f"\nAPI Error: {result['error']}")
    else:
        print(f"\n{'='*60}")
        print("No API key provided. To test:")
        print(f"  python {__file__} --api-key sk-YOUR_KEY_HERE")
        print(f"\nOr with curl:")
        print(f'  curl -X POST "{API_URL}" \\')
        print(f'    -H "Content-Type: application/json" \\')
        print(f'    -H "Authorization: Bearer sk-YOUR_KEY" \\')
        print(f'    -d @paddleocr_vl_request.json')
        print(f"{'='*60}")


if __name__ == "__main__":
    main()
