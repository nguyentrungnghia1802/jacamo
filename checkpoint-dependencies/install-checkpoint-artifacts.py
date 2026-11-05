#!/usr/bin/env python3
"""Install pinned checkpoint runtime artifacts into local Maven repository.
Validates SHA-256 integrity before installation.
"""
import hashlib
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO_DIR = ROOT / 'artifacts'

ARTIFACTS = [
    {
        'jar': 'checkpoint-runtime-1.0.0-SNAPSHOT.jar',
        'pom': 'checkpoint-runtime.pom',
        'sha256': 'be844afb1706ac65976fa8b9fbaf3e6e3b39214c9c6067e44cc33b9b2ef77ee6',
    },
    {
        'jar': 'cartago-3.1-checkpoint-SNAPSHOT.jar',
        'pom': 'cartago.pom',
        'sha256': '888f431d03311fa5777b671455b4de3c15179540aa058d3e73cea066f2f37b82',
    },
    {
        'jar': 'npl-0.6.1-checkpoint-SNAPSHOT.jar',
        'pom': 'npl.pom',
        'sha256': '55a1417b6937ec66b22ac1c7159c4408623e31642bc7c92d517b4bfc78f8ddcc',
    },
    {
        'jar': 'jason-interpreter-3.3.2-checkpoint-SNAPSHOT.jar',
        'pom': 'jason-interpreter.pom',
        'sha256': '135dc5e0ca1cafea23ac891b0b45f2673d36990d7711f0d5605705b23998de72',
    },
    {
        'jar': 'jacamo-1.3.1-checkpoint-SNAPSHOT.jar',
        'pom': 'jacamo-1.3.1-checkpoint-SNAPSHOT.pom',
        'sha256': 'aca1163c73b854123526f52c22c0ea8c6f9bc772376c5fb73d20a1aa4c6af155',
    },
]

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().lower()

def main():
    mvn_cmd = 'mvn.cmd' if os.name == 'nt' else 'mvn'
    print(f"Installing pinned checkpoint artifacts from {REPO_DIR}...")
    for item in ARTIFACTS:
        jar_path = REPO_DIR / item['jar']
        pom_path = REPO_DIR / item['pom']
        if not jar_path.is_file():
            print(f"ERROR: missing {jar_path}", file=sys.stderr)
            sys.exit(1)
        if not pom_path.is_file():
            print(f"ERROR: missing {pom_path}", file=sys.stderr)
            sys.exit(1)
        actual_sha = sha256_file(jar_path)
        if actual_sha != item['sha256']:
            print(f"ERROR: sha256 mismatch for {item['jar']}: expected {item['sha256']}, got {actual_sha}", file=sys.stderr)
            sys.exit(1)
        cmd = [
            mvn_cmd, '-B',
            'org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file',
            f'-Dfile={jar_path}',
            f'-DpomFile={pom_path}'
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if res.returncode != 0:
            print(res.stdout, file=sys.stderr)
            print(f"ERROR: failed to install {item['jar']}", file=sys.stderr)
            sys.exit(res.returncode)
        print(f"Installed {item['jar']} ({actual_sha[:12]}...)")
    print("All pinned checkpoint artifacts installed successfully.")

if __name__ == '__main__':
    main()
