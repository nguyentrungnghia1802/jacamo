"""Create narrow source overlays from pinned upstream sources. Never edit a case."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile

base=Path(__file__).resolve().parent
workspace=base.parent.parent
sources=base/'src'
origin=[]
def sha(b):return hashlib.sha256(b).hexdigest()
def copy(repo,part,rel):
    data=(workspace/'checkpoint-deps'/repo/rel).read_bytes()
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=workspace/'checkpoint-deps'/repo,text=True).strip()
    origin.append(dict(component=part,path=rel,commit=commit,sha256=sha(data)))
    return data.decode().replace('\r\n','\n')
def write(part,rel,text):
    p=sources/part/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8',newline='\n')
def end_brace(text,start):
    # Ignore braces in comments/strings; upstream Java has no text blocks here.
    level=1;i=start+1;state='code'
    while i<len(text):
        c=text[i];n=text[i:i+2]
        if state=='line':
            if c=='\n':state='code'
        elif state=='block':
            if n=='*/':state='code';i+=1
        elif state in ['"',"'"]:
            if c=='\\':i+=1
            elif c==state:state='code'
        elif n=='//':state='line';i+=1
        elif n=='/*':state='block';i+=1
        elif c in ['"',"'"]:state=c
        elif c=='{':level+=1
        elif c=='}':
            level-=1
            if not level:return i
        i+=1
    raise ValueError('unbalanced Java')
def methods(text,names,lane):
    pattern=re.compile(r'(?m)^(\s*)(public|private|protected)\s+(synchronized\s+)?([\w<>\[\].]+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[\w.,\s]+)?\{')
    matches=[m for m in pattern.finditer(text) if m.group(5) in names]
    for m in reversed(matches):
        start=m.end()-1;end=end_brace(text,start);header=m.group(0);body=text[start+1:end]
        is_sync=bool(m.group(3));header=header.replace('synchronized ','')
        wrapped='\ntry(var checkpointStep=ExecutionBarrier.enter("'+lane+'.'+m.group(5)+'")) {\n'
        if is_sync:wrapped+='synchronized(this) {\n'+body+'\n}\n'
        else:wrapped+=body
        text=text[:m.start()]+header+wrapped+'\n}\n'+text[end:]
    return text
def imported(text):return text.replace('\n\n','\n\nimport org.jacamo.checkpoint.runtime.ExecutionBarrier;\n',1)
def marker(text):
    pos=text.rfind('}')
    return text[:pos]+'\npublic static String checkpointHookVersion(){return ExecutionBarrier.VERSION;}\n'+text[pos:]

artifact=imported(copy('cartago','cartago','src/main/java/cartago/Artifact.java'))
artifact=methods(artifact,{'doOperation'},'cartago')
# Every guard wakeup must relinquish the artifact lock before waiting for admission.
artifact=artifact.replace('guards.await();','checkpointAwaitGuard();')
pos=artifact.rfind('}')
artifact=artifact[:pos]+'''
private void checkpointAwaitGuard() throws InterruptedException {
    if(!ExecutionBarrier.enabled()){guards.await();return;}
    var suspended=ExecutionBarrier.suspend();
    try {guards.await();}
    finally {lock.unlock();try{suspended.resume();}finally{lock.lock();}}
}
'''+artifact[pos:]
# Source waits yield the current step and reacquire admission outside artifact locks.
for name in ['await_time','await','execLinkedOp']:
    pattern=re.compile(r'(?m)^\s*protected void '+name+r'\([^)]*\)[^{]*\{')
    for m in reversed(list(pattern.finditer(artifact))):
        start=m.end()-1;end=end_brace(artifact,start);body=artifact[start+1:end]
        if 'lock.unlock();' not in body:continue
        body='\nExecutionBarrier.Suspension checkpointWait=null;\n'+body
        body=body.replace('lock.unlock();','checkpointWait=ExecutionBarrier.suspend();\nlock.unlock();')
        body=body.replace('lock.lock();','try{if(checkpointWait!=null)checkpointWait.resume();}finally{lock.lock();}')
        body=body.replace('Thread.sleep(dt);','ExecutionBarrier.sleep(dt);')
        artifact=artifact[:start+1]+body+artifact[end:]
artifact=artifact.replace('public void beginExtSession(){','public void beginExtSession(){\ncheckpointExternalSteps.get().push(ExecutionBarrier.enter("cartago.external-session"));')
artifact=artifact.replace('lock.unlock();\n\t\t}\n\t}', 'lock.unlock();\n\t\t}\n\t}') # retained upstream formatting
# External permits must be closed after unlocking, on both success/failure paths.
for name in ['endExtSession','endExtSessionWithFailure']:
    m=re.search(r'public void '+name+r'\(\)\s*\{',artifact);start=m.end()-1;end=end_brace(artifact,start)
    body=artifact[start+1:end].replace('lock.unlock();','lock.unlock();\ncheckpointExternalSteps.get().pop().close();')
    artifact=artifact[:start+1]+body+artifact[end:]
pos=artifact.rfind('}')
artifact=artifact[:pos]+'\nprivate final ThreadLocal<java.util.Deque<ExecutionBarrier.Step>> checkpointExternalSteps=ThreadLocal.withInitial(java.util.ArrayDeque::new);\n'+artifact[pos:]
artifact=artifact.replace('System.currentTimeMillis()','ExecutionBarrier.nowMillis()')
# Preserve lock ownership on interrupted/failed timed waits as well as normal wakeup.
m=re.search(r'protected void await_time\(long dt\)\s*\{',artifact);start=m.end()-1;end=end_brace(artifact,start)
artifact=artifact[:start+1]+'''
    OpId id=thisOpId;
    try {
        commitObsStateChanges();
        var suspended=ExecutionBarrier.suspend();lock.unlock();
        try{ExecutionBarrier.sleep(dt);}
        finally{try{suspended.resume();}finally{lock.lock();}}
        restoreOpExecContext(id);
    }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalArgumentException("Interrupted artifact timed wait",interrupted);}
'''+artifact[end:]
write('cartago','cartago/Artifact.java',marker(artifact))

workspace_source=imported(copy('cartago','cartago','src/main/java/cartago/Workspace.java'))
workspace_source=methods(workspace_source,{'serveOperation','createWorkspace','createRemoteWorkspace','createWorkspaceOnRemoteNode','linkWorkspace','linkLocalWorkspace','linkRemoteWorkspace','startSession','closeSession','joinWorkspace','bindAgentBodyArtifact','quitAgent','makeArtifact','disposeArtifact','linkArtifacts','removeGarbageBody','notifyAgentPositionOrRadiusChange','notifyArtifactPositionOrRadiusChange'},'cartago.workspace')
workspace_source=workspace_source.replace('ExecutionBarrier.enter("cartago.workspace.serveOperation")','ExecutionBarrier.enterQueued(info,"cartago.workspace.serveOperation")')
workspace_source=re.sub(r'opTodo\.put\((\w+)\);',r'ExecutionBarrier.queued(\1);opTodo.put(\1);',workspace_source)
workspace_source=workspace_source.replace('System.currentTimeMillis()','ExecutionBarrier.nowMillis()')
write('cartago','cartago/Workspace.java',marker(workspace_source))
environment=imported(copy('cartago','cartago','src/main/java/cartago/CartagoEnvironment.java'))
environment=methods(environment,{'init','initFromRemote','startSession','createRemoteWorkspace','joinRemoteWorkspace'},'cartago.environment')
write('cartago','cartago/CartagoEnvironment.java',marker(environment))

for name in ['TimeTerm','NormInstance','StateTransitions','StateTransitionsThread','NPLInterpreter']:
    text=imported(copy('npl','npl','src/main/java/npl/'+name+'.java'))
    text=text.replace('System.currentTimeMillis()','ExecutionBarrier.nowMillis()').replace('new Date()','new Date(ExecutionBarrier.nowMillis())')
    if name=='NPLInterpreter':
        text=methods(text,{'loadNP','addNorm','clearFacts','removeFact','addFact','verifyNorms','setAllActivatedNorms'},'npl')
        text=text.replace('exec.execute(() -> {','ExecutionBarrier.dispatch(exec, () -> {')
    if name=='StateTransitionsThread':
        text=text.replace('private boolean running = true;','private volatile boolean running = true;')
        text=text.replace('synchronized void update() {','synchronized void update() {\nExecutionBarrier.queued(this);')
        text=text.replace('super.deadlineAchieved(o);','super.deadlineAchieved(o);\nExecutionBarrier.queued(this);')
        text=text.replace('synchronized public void run() {','public void run() {\nif(ExecutionBarrier.enabled()){checkpointRun();return;}\ncheckpointLegacyRun();\n}\nprivate synchronized void checkpointLegacyRun() {')
        text=text.replace('scheduler.schedule(() -> deadlineAchieved(o), ttf, TimeUnit.MILLISECONDS);','if(ExecutionBarrier.enabled())ExecutionBarrier.schedule(() -> {if(running)deadlineAchieved(o);},ttf);\nelse scheduler.schedule(() -> deadlineAchieved(o), ttf, TimeUnit.MILLISECONDS);')
        pos=text.rfind('}')
        text=text[:pos]+'''
private void checkpointRun() {
    while(running) {
        try {
            synchronized(this){wait(Math.max(1,updateInterval));}
            try(var step=ExecutionBarrier.enterQueued(this,"npl.state-transitions")) {
                synchronized(engine.syncTransState){super.update();}
            }
        }catch(InterruptedException interrupted){return;}
        catch(ExecutionBarrier.Stopped stopped){return;}
        catch(ConcurrentModificationException retry){/* original updater retries */}
    }
}
'''+text[pos:]
    write('npl','npl/'+name+'.java',marker(text))

jasonjar=base.parent/'build/checkpoint-source/jason-interpreter-3.3.2-sources.jar'
assert sha(jasonjar.read_bytes())=='2f4e450d1fb116a9bf2d4ab7eddb2265b82fa4a303a97a64570c67710e2d1ce0'
with zipfile.ZipFile(jasonjar) as z:
    for rel in ['jason/stdlib/wait.java','jason/infra/local/LocalAgArch.java','jason/asSemantics/TransitionSystem.java']:
        data=z.read(rel);origin.append(dict(component='jason',path=rel,sourceJarSha256=sha(jasonjar.read_bytes()),sha256=sha(data)))
        text=imported(data.decode().replace('\r\n','\n'))
        if rel.endswith('/wait.java'):
            text=text.replace('System.currentTimeMillis()','ExecutionBarrier.nowMillis()')
            text=text.replace('Agent.getScheduler().schedule(() -> {\n                    resume(true);\n                }, timeout, TimeUnit.MILLISECONDS);','if(ExecutionBarrier.enabled())ExecutionBarrier.schedule(() -> resume(true),timeout);\nelse Agent.getScheduler().schedule(() -> resume(true),timeout,TimeUnit.MILLISECONDS);')
        elif rel.endswith('/LocalAgArch.java'):
            text=methods(text,{'reasoningCycle','receiveMsg'},'jason.local-cycle')
            text=text.replace('ExecutionBarrier.enter("jason.local-cycle.reasoningCycle")','ExecutionBarrier.enterQueued(this,"jason.local-cycle.reasoningCycle")')
            text=text.replace('            ts.act();','            ts.act();\n            if (ExecutionBarrier.admissionClosed()) break;')
            text=text.replace('        getFirstAgArch().reasoningCycleFinished();','        getFirstAgArch().reasoningCycleFinished();\n        checkpointTaskWaits();')
            text=text.replace('    public void wake() {','    public void wake() {\n        if (running && myThread != null && myThread.isAlive()) ExecutionBarrier.queued(this);')
            pos=text.rfind('}')
            text=text[:pos]+'''
    private void checkpointTaskWaits() {
        if (!ExecutionBarrier.enabled()) return;
        var circumstance=getTS().getC();
        var waiting=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        waiting.addAll(circumstance.getPendingIntentions().values());
        circumstance.getPendingActions().values().forEach(action->waiting.add(action.getIntention()));
        boolean atomicBlocked=circumstance.isAtomicIntentionSuspended();
        if(circumstance.hasAtomicIntention()) {
            var all=circumstance.getRunningIntentionsPlusAtomic();
            while(all.hasNext()) {var intention=all.next();if(intention.isAtomic()&&!ExecutionBarrier.intentionAllowed(intention)){atomicBlocked=true;break;}}
        }
        ExecutionBarrier.taskWaits(this,waiting,atomicBlocked);
    }
'''+text[pos:]
        else:
            old='C.SI = ag.selectIntention(C.getRunningIntentions());'
            assert text.count(old)==1
            text=text.replace(old,'C.SI = checkpointSelectIntention();')
            old='''        C.SI = C.removeAtomicIntention();
        if (C.SI != null) {
            return;
        }'''
            assert text.count(old)==1
            text=text.replace(old,'''        C.SI = C.removeAtomicIntention();
        if (C.SI != null) {
            if (!ExecutionBarrier.intentionAllowed(C.SI)) {
                C.setAtomicIntention(C.SI);
                C.SI = null;
                stepAct = State.StartRC;
            }
            return;
        }''')
            pos=text.rfind('}')
            text=text[:pos]+'''
    private Intention checkpointSelectIntention() {
        var running=C.getRunningIntentions();
        if (!ExecutionBarrier.admissionClosed()) return ag.selectIntention(running);
        var eligible=new java.util.ArrayDeque<Intention>();
        for (var intention:running) if (ExecutionBarrier.intentionAllowed(intention)) eligible.add(intention);
        var selected=ag.selectIntention(eligible);
        return selected!=null&&running.remove(selected)?selected:null;
    }
'''+text[pos:]
        write('jason',rel,marker(text))
(base/'source-provenance.json').write_text(json.dumps(origin,indent=2)+'\n')
print('Prepared',len(origin),'source overlays with pinned provenance')
