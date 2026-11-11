"""Build pinned source overlays into separately versioned local Maven artifacts.
Original JAR entries are retained except classes rebuilt from the reviewed source
overlays. No official Maven coordinate is overwritten; no network is required.
"""
from pathlib import Path
import hashlib,json,os,subprocess,zipfile,xml.etree.ElementTree as ET
base=Path(__file__).resolve().parent
repo=base.parent
build=repo/'build/checkpoint-dependencies';build.mkdir(parents=True,exist_ok=True)
m2=Path.home()/'.m2/repository'
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
classpath=(repo.parent/'use/use-plugin/modules/bridge-jacamo/target/jacamo-bridge-classpath.txt').read_text().strip().split(os.pathsep)
classpath=[p for p in classpath if p.endswith('.jar')]
asm=m2/'org/ow2/asm/asm/9.7/asm-9.7.jar'
assert asm.is_file(),'Pinned ASM 9.7 is required for admitted project clock weaving'
classpath.append(str(asm))
components=[('cartago','org.jacamo','cartago','3.1','bf03b37b28e304b2d784c8d77267e56c12b943ff70eed35b533c6323e0736866'),('npl','org.jacamo','npl','0.6.1','956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332'),('jason','io.github.jason-lang','jason-interpreter','3.3.2','cd79268ddcfa45599284bda64bd6980dbd86eec6c862028cc276495aab49154c')]
def compile(part,source):
    classes=build/(part+'-classes');classes.mkdir(parents=True,exist_ok=True)
    files=sorted(source.rglob('*.java'))
    cp=os.pathsep.join([str(build/'runtime-classes'),*classpath])
    subprocess.run(['javac','-g','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*[str(p) for p in files]],check=True)
    return classes
def pack(path,entries):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(entries.items()):
            item=zipfile.ZipInfo(name,(2026,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;z.writestr(item,data)
def add_classes(entries,classes):
    for p in sorted(classes.rglob('*.class')):entries[p.relative_to(classes).as_posix()]=p.read_bytes()
def install(jar,pom):
    result=subprocess.run(['mvn.cmd' if os.name=='nt' else 'mvn','-B','org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(jar),'-DpomFile='+str(pom)],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if result.returncode:print(result.stdout);result.check_returncode()
def kernel_pom():
    return '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>org.jacamo</groupId><artifactId>checkpoint-runtime</artifactId><version>1.0.0-SNAPSHOT</version><dependencies><dependency><groupId>org.ow2.asm</groupId><artifactId>asm</artifactId><version>9.7</version></dependency></dependencies></project>'
runtime=compile('runtime',base/'runtime/src/main/java')
runtimejar=build/'checkpoint-runtime-1.0.0-SNAPSHOT.jar';entries={}
add_classes(entries,runtime)
entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nImplementation-Version: 1.0.0-SNAPSHOT\r\n\r\n'
pack(runtimejar,entries);runtimepom=build/'checkpoint-runtime.pom';runtimepom.write_text(kernel_pom());install(runtimejar,runtimepom)
record={'version':'CHECKPOINT_DEPENDENCY_LOCK/1.0','runtime':{'path':str(runtimejar),'sha256':sha(runtimejar)},'components':[]}
for part,group,artifact,version,pinned in components:
    original=m2/Path(group.replace('.','/'))/artifact/version/(artifact+'-'+version+'.jar')
    assert sha(original)==pinned,(part,'original binary hash mismatch')
    classes=compile(part,base/'src'/part)
    with zipfile.ZipFile(original) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    newversion=version+'-checkpoint-SNAPSHOT'
    manifest=entries.get('META-INF/MANIFEST.MF',b'Manifest-Version: 1.0\r\n\r\n').decode().replace('\r\n','\n').strip()
    keep=[];skip=False
    for line in manifest.splitlines():
        if not line.startswith(' '):skip=line.lower().startswith(('implementation-version:','checkpoint-original-sha256:'))
        if not skip:keep.append(line)
    manifest='\n'.join(keep)
    manifest+='\nImplementation-Version: '+newversion+'\nCheckpoint-Original-SHA256: '+pinned+'\n'
    # Jar manifest lines are limited to 72 bytes, including continuation markers.
    lines=[]
    for line in manifest.splitlines():
        while len(line)>70:lines.append(line[:70]);line=' '+line[70:]
        lines.append(line)
    entries['META-INF/MANIFEST.MF']=('\r\n'.join(lines)+'\r\n\r\n').encode()
    roots=[p.relative_to(classes).as_posix()[:-6] for p in classes.rglob('*.class') if '$' not in p.name]
    entries={n:b for n,b in entries.items() if not any(n==r+'.class' or n.startswith(r+'$') and n.endswith('.class') for r in roots)}
    add_classes(entries,classes)
    entries['META-INF/checkpoint-source-provenance.json']=(base/'source-provenance.json').read_bytes()
    jar=build/(artifact+'-'+newversion+'.jar');pack(jar,entries)
    ns={'p':'http://maven.apache.org/POM/4.0.0'};ET.register_namespace('',ns['p']);tree=ET.parse(original.with_suffix('.pom'));pomroot=tree.getroot();prefix='{'+ns['p']+'}'
    pomroot.find('p:version',ns).text=newversion
    dependencies=pomroot.find('p:dependencies',ns)
    if dependencies is None:dependencies=ET.SubElement(pomroot,prefix+'dependencies')
    dependency=ET.SubElement(dependencies,prefix+'dependency')
    for name,value in [('groupId','org.jacamo'),('artifactId','checkpoint-runtime'),('version','1.0.0-SNAPSHOT')]:ET.SubElement(dependency,prefix+name).text=value
    pom=build/(artifact+'.pom');tree.write(pom,encoding='utf-8',xml_declaration=True);install(jar,pom)
    record['components'].append({'component':part,'groupId':group,'artifactId':artifact,'version':newversion,'originalSha256':pinned,'sha256':sha(jar),'path':str(jar),'overlayClasses':[p.relative_to(classes).as_posix() for p in sorted(classes.rglob('*.class'))]})
(base/'dependency-lock.json').write_text(json.dumps(record,indent=2)+'\n')
identity=repo/'src/main/resources/META-INF/checkpoint-binaries.properties';identity.parent.mkdir(parents=True,exist_ok=True)
identity.write_text('version=1.0.0\nruntime='+sha(runtimejar)+'\n'+''.join(c['component']+'='+c['sha256']+'\n' for c in record['components']))
profiles=repo.parent/'use/use-plugin/modules/bridge-contract/src/main/resources/META-INF/checkpoint-class-profiles.properties';profiles.parent.mkdir(parents=True,exist_ok=True)
profiles.write_text('version=1.0.0\ncartago.Artifact='+sha(build/'cartago-classes/cartago/Artifact.class')+'\ncartago.Workspace='+sha(build/'cartago-classes/cartago/Workspace.class')+'\nnpl.NormInstance='+sha(build/'npl-classes/npl/NormInstance.class')+'\nnpl.jar='+next(c['sha256'] for c in record['components'] if c['component']=='npl')+'\n')
print(json.dumps(record,indent=2))
