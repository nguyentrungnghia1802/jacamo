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
        'sha256': '3fbf98bc1bad6d0e48a70bb0d6296d0d4bdaff9432949bb843f355a523b51018',
    },
    {
        'jar': 'cartago-3.1-checkpoint-SNAPSHOT.jar',
        'pom': 'cartago.pom',
        'sha256': 'b41bf68920c95d2cf903c5222c615a73ad7e28f7598952870668cec814d0f16a',
    },
    {
        'jar': 'npl-0.6.1-checkpoint-SNAPSHOT.jar',
        'pom': 'npl.pom',
        'sha256': 'd1666fbdaa816d99b6837c0dd68ce57b3f30815b460e8cd9ad295f4695408056',
    },
    {
        'jar': 'jason-interpreter-3.3.2-checkpoint-SNAPSHOT.jar',
        'pom': 'jason-interpreter.pom',
        'sha256': 'e1a7a84bf20018189d660a19ad0cf65fef2e5ab2a9417061a28608cbb28d2646',
    },
    {
        'jar': 'jacamo-1.3.1-checkpoint-SNAPSHOT.jar',
        'pom': 'jacamo-1.3.1-checkpoint-SNAPSHOT.pom',
        'sha256': 'dede2e2cde568c747a774e33f821cdcee6501515997a710816aa9c02c7c901f5',
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
