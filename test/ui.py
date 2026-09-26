"""UI 自动化辅助：在模拟器屏幕上按文字找到元素并点按其中心。
用法:
  python ui.py dump                 # 导出当前 UI 到 ui.xml 并打印可点击节点文本
  python ui.py tap 文本             # 点按包含指定文本的元素中心
  python ui.py tapxy x y            # 按坐标点按
"""
import re
import subprocess
import sys

ADB = r"D:\Android\Sdk\platform-tools\adb.exe"


def sh(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


def dump() -> str:
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return sh("shell", "cat", "/sdcard/ui.xml")


def parse_nodes(xml: str):
    # node: text, content-desc, clickable, bounds
    out = []
    for m in re.finditer(r"<node[^>]*/?>", xml):
        tag = m.group(0)
        text = _attr(tag, "text")
        desc = _attr(tag, "content-desc")
        clickable = 'clickable="true"' in tag
        bounds = _attr(tag, "bounds")
        if text or desc:
            out.append({"text": text or desc, "clickable": clickable, "bounds": bounds})
    return out


def _attr(tag: str, name: str):
    m = re.search(name + r'="([^"]*)"', tag)
    return m.group(1) if m else ""


def center(bounds: str):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", bounds)
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2


def tap_text(needle: str):
    xml = dump()
    nodes = parse_nodes(xml)
    # 优先精确匹配，其次包含
    candidates = [n for n in nodes if n["text"] == needle] or [n for n in nodes if needle in n["text"]]
    if not candidates:
        print(f"NOT FOUND: {needle}")
        print("---- 可见文本 ----")
        for n in nodes:
            print(repr(n["text"]), n["bounds"])
        sys.exit(1)
    c = center(candidates[0]["bounds"])
    sh("shell", "input", "tap", str(c[0]), str(c[1]))
    print(f"tapped {needle} at {c}")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "dump":
        xml = dump()
        for n in parse_nodes(xml):
            print(("CLICK " if n["clickable"] else "      ") + repr(n["text"]) + " " + n["bounds"])
    elif cmd == "tap":
        tap_text(sys.argv[2])
    elif cmd == "tapxy":
        sh("shell", "input", "tap", sys.argv[2], sys.argv[3])
        print(f"tapped {sys.argv[2]},{sys.argv[3]}")
