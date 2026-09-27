"""Download the raw LDS survey files from the CIMMYT Dataverse and verify their MD5 checksums.

Usage (from ml-service/):  python -m training.download_data
"""

from __future__ import annotations

import hashlib
import sys
import urllib.request

from training.datasets import DATASETS, RAW_DIR, Dataset


def md5_of(path) -> str:
    digest = hashlib.md5()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(ds: Dataset) -> None:
    if ds.path.exists() and md5_of(ds.path) == ds.md5:
        print(f"[ok]   {ds.filename} already present, checksum matches")
        return
    print(f"[get]  {ds.url} -> {ds.path}")
    tmp = ds.path.with_suffix(".part")
    # The Dataverse front end rejects urllib's default User-Agent with 403.
    request = urllib.request.Request(ds.url, headers={"User-Agent": "agrioptima-data-fetch/1.0"})
    with urllib.request.urlopen(request, timeout=300) as resp, open(tmp, "wb") as out:
        while chunk := resp.read(1 << 20):
            out.write(chunk)
    actual = md5_of(tmp)
    if actual != ds.md5:
        tmp.unlink()
        raise SystemExit(f"checksum mismatch for {ds.filename}: expected {ds.md5}, got {actual}. "
                         "The upstream file changed; review it before updating training/datasets.py.")
    tmp.replace(ds.path)
    print(f"[ok]   {ds.filename} md5 {actual}")


def main() -> int:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    for ds in DATASETS:
        fetch(ds)
    return 0


if __name__ == "__main__":
    sys.exit(main())
