# scripts

## `add_nav.py` — markdown navigation generator

Injects consistent, **idempotent** navigation links into the repo's markdown so
you can jump between topics easily. Re-run it any time after adding phases/tracks
— it only ever replaces the marker-delimited nav blocks, never duplicates them.

```bash
python3 scripts/add_nav.py
```

What it adds (between `<!-- ... -->` markers):
- **root `README.md`** → a "Tracks — quick navigation" index of every track.
- **each track `README.md`** → a "Repo Home" back-link + a "Phase files" index
  (links to each phase's Notes/Interview).
- **each phase `NOTES.md` / `INTERVIEW.md`** → a breadcrumb
  (Home · Track · Notes · Interview) plus ⬅ prev / next ➡ phase links.

Detection: tracks are top-level `NN-*` dirs with a `README.md`; phases are
sub-dirs containing a `NOTES.md` or `INTERVIEW.md`, ordered by their number.
