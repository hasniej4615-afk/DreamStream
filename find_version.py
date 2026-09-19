import os

for root, dirs, files in os.walk('app'):
    for file in files:
        if file.endswith('.kt') or file.endswith('.kts') or file.endswith('.xml'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                try:
                    content = f.read()
                    if '1.9.2' in content:
                        print(f"Found 1.9.2 in {path}")
                    if 'changelog' in content.lower():
                        print(f"Found changelog in {path}")
                except:
                    pass
