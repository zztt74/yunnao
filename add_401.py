import re

with open('contracts/openapi.yaml', 'r', encoding='utf-8') as f:
    content = f.read()

# Count before
old_count = content.count("'401'")

# Strategy: find every "responses:" block ending pattern and insert 401 after it
# Pattern 1: $ref to Response schema at end of 200 block -> insert 401 after
resp401 = """
      '401':
        description: 未认证或Token已过期
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ApiResponse'
"""

# Match: last line of 200-response content (schema $ref or type line) followed by blank line or next path
# These are the patterns where 200 block ends:

# Pattern A: ...$ref: './schemas/xxxResponse'\n\n  /api/ or \ncomponents:
count = 0
def replacer(m):
    global count
    count += 1
    return m.group(0).rstrip('\n') + resp401 + '\n'

content = re.sub(
    r"(\$ref: '\./schemas/.*?Response'\n)(\n)(\s*(?:/api|components))",
    lambda m: m.group(1) + resp401 + '\n' + m.group(3),
    content
)

# Pattern B: For create/update operations, the 200 block ends after description
# e.g., description: 创建成功\n      (followed by next path)
# These have requestBody with Request schemas
content = re.sub(
    r"(description: (?:创建|更新|注册|修改|取消|登录|登出|查询|获取|删除|成功)[^\n]*\n)(\n\s*(?:/api|components))",
    lambda m: m.group(1) + resp401 + '\n' + m.group(2),
    content
)

new_count = content.count("'401'")
print(f"Added {new_count - old_count} 401 responses")

with open('contracts/openapi.yaml', 'w', encoding='utf-8') as f:
    f.write(content)
