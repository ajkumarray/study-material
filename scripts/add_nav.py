#!/usr/bin/env python3
"""Inject idempotent navigation links into the repo's markdown files.

Adds, between <!-- nav --> ... <!-- /nav --> markers (so re-runs replace, never
duplicate):
  * root README:  a "Tracks" index linking every track README
  * track README: a back-link to repo home + a phase index (NOTES/INTERVIEW links)
  * phase NOTES.md / INTERVIEW.md: breadcrumb (home / track / notes / interview)
    plus prev/next phase links.
"""
import re
from pathlib import Path

ROOT = Path("/home/ajkumarray/Projects/practice/tech-stack")
NAV_START, NAV_END = "<!-- nav -->", "<!-- /nav -->"
TRACKS_START, TRACKS_END = "<!-- tracks-nav -->", "<!-- /tracks-nav -->"
PHASES_START, PHASES_END = "<!-- phases-nav -->", "<!-- /phases-nav -->"

def num_key(p: Path):
    m = re.search(r"(\d+)", p.name)
    return (int(m.group(1)) if m else 9999, p.name)

def strip_block(text, start, end):
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end) + r"\n*", re.DOTALL)
    return pattern.sub("", text)

def set_top_block(text, start, end, body):
    """Replace an existing start..end block or insert one at the very top."""
    text = strip_block(text, start, end)
    block = f"{start}\n{body}\n{end}\n\n"
    return block + text.lstrip("\n")

def set_named_block(text, start, end, body):
    """Replace an existing named block in place, else append at the end."""
    block = f"{start}\n{body}\n{end}"
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.DOTALL)
    if pattern.search(text):
        return pattern.sub(block, text)
    return text.rstrip("\n") + "\n\n" + block + "\n"

def phase_title(name: str) -> str:
    # phase-4-idempotency -> "Phase 4 · idempotency"
    m = re.match(r"phase-(\d+)-(.*)", name)
    if m:
        return f"Phase {m.group(1)} · {m.group(2).replace('-', ' ')}"
    return name

# ---- discover tracks (top-level NN-*) and their phase dirs (contain NOTES.md) ----
tracks = sorted([d for d in ROOT.iterdir()
                 if d.is_dir() and re.match(r"\d\d-", d.name) and (d / "README.md").exists()],
                key=num_key)

changed = 0

for track in tracks:
    phases = sorted([d for d in track.iterdir()
                     if d.is_dir() and ((d / "NOTES.md").exists() or (d / "INTERVIEW.md").exists())],
                    key=num_key)

    # ---- per-phase NOTES/INTERVIEW breadcrumbs + prev/next ----
    for i, ph in enumerate(phases):
        prev_ph = phases[i - 1] if i > 0 else None
        next_ph = phases[i + 1] if i < len(phases) - 1 else None
        for fname in ("NOTES.md", "INTERVIEW.md"):
            f = ph / fname
            if not f.exists():
                continue
            crumb = ["**[🏠 Home](../../README.md)**", "**[📚 Track](../README.md)**"]
            if (ph / "NOTES.md").exists():
                crumb.append("[📖 Notes](./NOTES.md)")
            if (ph / "INTERVIEW.md").exists():
                crumb.append("[💬 Interview](./INTERVIEW.md)")
            line1 = " · ".join(crumb)
            tgt = "NOTES.md" if (ph / "NOTES.md").exists() else "INTERVIEW.md"
            nav2 = []
            if prev_ph:
                pt = "NOTES.md" if (prev_ph / "NOTES.md").exists() else "INTERVIEW.md"
                nav2.append(f"⬅ [{phase_title(prev_ph.name)}](../{prev_ph.name}/{pt})")
            if next_ph:
                nt = "NOTES.md" if (next_ph / "NOTES.md").exists() else "INTERVIEW.md"
                nav2.append(f"[{phase_title(next_ph.name)} ➡](../{next_ph.name}/{nt})")
            body = line1 + ("  \n" + " | ".join(nav2) if nav2 else "")
            text = f.read_text()
            new = set_top_block(text, NAV_START, NAV_END, body)
            if new != text:
                f.write_text(new); changed += 1

    # ---- track README: home link + phase index ----
    rp = track / "README.md"
    text = rp.read_text()
    text = set_top_block(text, NAV_START, NAV_END, "**[🏠 Repo Home](../README.md)**")
    if phases:
        lines = ["## 📂 Phase files", ""]
        for ph in phases:
            links = []
            if (ph / "NOTES.md").exists():
                links.append(f"[Notes]({ph.name}/NOTES.md)")
            if (ph / "INTERVIEW.md").exists():
                links.append(f"[Interview]({ph.name}/INTERVIEW.md)")
            lines.append(f"- **{phase_title(ph.name)}** — " + " · ".join(links))
        text = set_named_block(text, PHASES_START, PHASES_END, "\n".join(lines))
    new = text
    if new != rp.read_text():
        rp.write_text(new); changed += 1

# ---- root README: tracks index ----
root_readme = ROOT / "README.md"
text = root_readme.read_text()
lines = ["## 📂 Tracks — quick navigation", ""]
for t in tracks:
    # pull the track's H1 title if present
    title = t.name
    head = (t / "README.md").read_text().splitlines()
    for ln in head:
        if ln.startswith("# "):
            title = ln[2:].strip(); break
    lines.append(f"- [{title}]({t.name}/README.md)")
text = set_named_block(text, TRACKS_START, TRACKS_END, "\n".join(lines))
if text != root_readme.read_text():
    root_readme.write_text(text); changed += 1

print(f"updated {changed} files across {len(tracks)} tracks")
for t in tracks:
    n = len([d for d in t.iterdir() if d.is_dir() and ((d/'NOTES.md').exists() or (d/'INTERVIEW.md').exists())])
    print(f"  {t.name}: {n} phases")
