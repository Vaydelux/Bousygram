#!/usr/bin/env python3
"""Validate launcher routes in compiled aapt2 XML tree, without changing APK bytes."""
import re
import sys

MAIN = "com.vaydelux.bousygram.app.MainActivity"
PACKAGE = "com.vaydelux.bousygram"
CATEGORIES = ("android.intent.category.LAUNCHER", "android.intent.category.LEANBACK_LAUNCHER")
ACTION = "android.intent.action.MAIN"

def verify(text):
    stack = []
    activities = []
    for line in text.splitlines():
        element = re.match(r"^(\s*)E:\s+([\w-]+)", line)
        if element:
            indent, tag = len(element[1]), element[2]
            while stack and stack[-1]["indent"] >= indent:
                stack.pop()
            node = {"indent": indent, "tag": tag, "name": None, "children": []}
            if stack:
                stack[-1]["children"].append(node)
            stack.append(node)
            if tag in ("activity", "activity-alias"):
                activities.append(node)
            continue
        attr = re.match(r'^\s*A:\s+android:name(?:\([^)]*\))?="([^"]+)"', line)
        if attr and stack:
            stack[-1]["name"] = attr[1]
    def normalized(name):
        if name is None:
            return None
        if name.startswith("."):
            return PACKAGE + name
        if "." not in name:
            return PACKAGE + "." + name
        return name
    for category in CATEGORIES:
        names = []
        for activity in activities:
            for filt in activity["children"]:
                if filt["tag"] != "intent-filter":
                    continue
                actions = {n["name"] for n in filt["children"] if n["tag"] == "action"}
                cats = {n["name"] for n in filt["children"] if n["tag"] == "category"}
                if ACTION in actions and category in cats:
                    names.append(normalized(activity["name"]))
        if names != [MAIN]:
            raise ValueError(f"Unexpected {category} launcher inventory: {names!r}")

if __name__ == "__main__":
    try:
        verify(sys.stdin.read())
    except ValueError as exc:
        sys.exit(str(exc))
    print("Verified single MainActivity phone and TV launcher")
