# -*- coding: utf-8 -*-
import io
import os
import xml.etree.ElementTree as ET

problems = []


def strip_kotlin(src):
    out = []
    i = 0
    n = len(src)
    while i < n:
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            if j < 0:
                return None
            i = j + 3
            out.append('R')
            continue
        c = src[i]
        if c == '"':
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                if src.startswith('${', i):
                    depth = 1
                    i += 2
                    while i < n and depth:
                        if src[i] == '{':
                            depth += 1
                        elif src[i] == '}':
                            depth -= 1
                        i += 1
                    continue
                i += 1
            out.append('S')
            continue
        if c == "'":
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == "'":
                    i += 1
                    break
                i += 1
            out.append('C')
            continue
        if src.startswith('//', i):
            j = src.find('\n', i)
            i = n if j < 0 else j
            continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)


kt_count = 0
for dirpath, _, files in os.walk("app/src/main/java"):
    for name in sorted(files):
        if not name.endswith(".kt"):
            continue
        kt_count += 1
        p = os.path.join(dirpath, name)
        with io.open(p, encoding="utf-8") as f:
            src = f.read()
        code = strip_kotlin(src)
        if code is None:
            problems.append((p, "unterminated raw string"))
            continue
        for op, cl in (("{", "}"), ("(", ")"), ("[", "]")):
            if code.count(op) != code.count(cl):
                problems.append((p, "unbalanced %s%s %d/%d" % (op, cl, code.count(op), code.count(cl))))
        if src.count('"""') % 2 != 0:
            problems.append((p, "odd raw-string fences"))
        if '""""' in src:
            problems.append((p, "4-quote sequence"))

xml_count = 0
for dirpath, _, files in os.walk("app/src/main"):
    for name in sorted(files):
        if not (name.endswith(".xml")):
            continue
        xml_count += 1
        p = os.path.join(dirpath, name)
        try:
            ET.parse(p)
        except Exception as e:
            problems.append((p, "xml: %s" % e))

yml_ok = None
try:
    import yaml
    with io.open(".github/workflows/build-release.yml", encoding="utf-8") as f:
        yml_ok = type(yaml.safe_load(f)).__name__
except ImportError:
    yml_ok = "yaml module missing"
except Exception as e:
    problems.append((".github/workflows/build-release.yml", "yaml: %s" % e))

print("kotlin files:", kt_count, "| xml files:", xml_count, "| yaml:", yml_ok)
if problems:
    print("--- problems")
    for p, m in problems:
        print(" ", p, "->", m)
else:
    print("no structural problems")
