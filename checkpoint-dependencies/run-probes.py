from pathlib import Path
import os,json,subprocess,sys
repo=Path(__file__).resolve().parent.parent;dep=repo/'checkpoint-dependencies';build=repo/'build/checkpoint-dependencies'
lock=json.loads((dep/'dependency-lock.json').read_text());cp=(repo.parent/'use/use-plugin/modules/bridge-jacamo/target/jacamo-bridge-classpath.txt').read_text().strip()
classpath=os.pathsep.join([str(build/'probe-classes'),lock['runtime']['path'],*[c['path'] for c in lock['components']],cp])
(build/'probe-classes').mkdir(exist_ok=True)
subprocess.run(['javac','-g','-proc:none','-cp',classpath,'-d',str(build/'probe-classes'),*[str(p) for p in sorted((dep/'tests').glob('*.java'))]],check=True)
output=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else repo.parent/'use/use-plugin/verification/verified-checkpoints/S01-extended-runtime';output.mkdir(parents=True,exist_ok=True)
commands=[('positive',['S01PositiveBoundaryProbe',str(output)]),('gui-boundary',['GenericGuiBoundaryProbe']),*[(kind,['S01LifecycleProbe',kind,str(output)]) for kind in ['admission','queue','notifications','stop']]]
for name,main in commands:
    with (output/(name+'.log')).open('w') as log:
        result=subprocess.run(['java','-Djava.awt.headless=true','-cp',classpath,*main],stdout=log,stderr=subprocess.STDOUT,timeout=35)
    print((output/(name+'.log')).read_text())
    result.check_returncode()
print('S01_EXTENDED_RUNTIME_PROBES_PASS')
