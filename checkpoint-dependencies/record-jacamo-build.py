"""Bind the locally published JaCaMo binary after Gradle jar/publishToMavenLocal."""
import hashlib, json, pathlib, zipfile, shutil, re
root = pathlib.Path(__file__).resolve().parents[1]
version = '1.3.1-checkpoint-SNAPSHOT'
jar = root / 'build/libs' / ('jacamo-' + version + '.jar')
installed = pathlib.Path.home() / '.m2/repository/org/jacamo/jacamo' / version / jar.name
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert jar.is_file() and installed.is_file() and digest(jar) == digest(installed), 'JACAMO_LOCAL_PUBLICATION_MISMATCH'
with zipfile.ZipFile(jar) as archive:
    assert b'Implementation-Version: '+version.encode() in archive.read('META-INF/MANIFEST.MF')
    assert 'jacamo/checkpoint/VerifiedCheckpointCoordinator.class' in archive.namelist()
lock_path = root / 'checkpoint-dependencies/dependency-lock.json'
lock = json.loads(lock_path.read_text())
lock['components'] = [c for c in lock['components'] if c['component'] != 'jacamo'] + [dict(component='jacamo', groupId='org.jacamo', artifactId='jacamo', version=version, path=str(jar), sha256=digest(jar))]
lock_path.write_text(json.dumps(lock, indent=2)+'\n')
# Keep the repository bootstrap artifacts identical to the tested publication.
artifacts=root/'checkpoint-dependencies/artifacts'
installer=root/'checkpoint-dependencies/install-checkpoint-artifacts.py'
source=installer.read_text()
for component in [lock['runtime']]+lock['components']:
    built=pathlib.Path(component['path'])
    assert digest(built)==component['sha256']
    shutil.copy2(built,artifacts/built.name)
    pattern=r"('jar': '"+re.escape(built.name)+r"',[\s\S]*?'sha256': ')[0-9a-f]{64}(')"
    source,count=re.subn(pattern,lambda m:m[1]+component['sha256']+m[2],source)
    assert count==1, built.name
installer.write_text(source)
print('JACAMO_LOCAL_PUBLICATION_MATCH', digest(jar))
