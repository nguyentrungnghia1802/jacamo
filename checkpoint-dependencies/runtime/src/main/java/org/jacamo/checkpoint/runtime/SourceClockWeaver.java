package org.jacamo.checkpoint.runtime;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;

/** Before any project class loads, bind compiled project clock calls to the shared clock.
 * Original authored source remains unchanged. Derived class hashes join admission evidence. */
public final class SourceClockWeaver {
    public static final String VERSION="ADMITTED_SOURCE_CLOCK/1.0";
    public record Proof(Set<String> sources,Map<String,String> classes,int sites){public Proof{sources=Set.copyOf(sources);classes=Map.copyOf(classes);}}
    private SourceClockWeaver(){}
    public static Proof prepare(Path root,Collection<Path> sources)throws Exception {
        Path exact=root.toRealPath(),classes=exact.resolve("build/classes/java/main");
        if(!Files.isDirectory(classes))return new Proof(Set.of(),Map.of(),0);
        var javaSources=new HashMap<String,String>();
        for(Path source:sources)if(source.toString().endsWith(".java")){
            var text=Files.readString(source);var matcher=java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(text);
            String key=(matcher.find()?matcher.group(1).replace('.','/')+"/":"")+source.getFileName();
            if(javaSources.putIfAbsent(key,exact.relativize(source.toRealPath()).toString().replace('\\','/'))!=null)throw new IllegalArgumentException("CHECKPOINT_AMBIGUOUS_JAVA_SOURCE:"+key);
        }
        var managed=new TreeSet<String>();var hashes=new TreeMap<String,String>();int total=0;
        List<Path> compiled;try(var walk=Files.walk(classes)){compiled=walk.filter(p->p.toString().endsWith(".class")).toList();}
        for(Path path:compiled){
            if(!path.toRealPath().startsWith(exact))throw new IllegalArgumentException("CHECKPOINT_CLASS_OUTSIDE_SOURCE_ROOT");
            byte[] original=Files.readAllBytes(path);var reader=new ClassReader(original);var writer=new ClassWriter(0);
            String name=reader.getClassName();String[] sourceFile={null};int[] sites={0};
            reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                @Override public void visitSource(String source,String debug){sourceFile[0]=source;super.visitSource(source,debug);}
                @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                    return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,method,descriptor,signature,exceptions)){
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean iface){
                            if(opcode==Opcodes.INVOKESTATIC&&owner.equals("java/lang/System")&&descriptor.equals("()J")&&Set.of("currentTimeMillis","nanoTime").contains(method)){
                                sites[0]++;super.visitMethodInsn(opcode,"org/jacamo/checkpoint/runtime/ExecutionBarrier",method.equals("currentTimeMillis")?"nowMillis":"nowNanos",descriptor,false);
                            }else super.visitMethodInsn(opcode,owner,method,descriptor,iface);
                        }
                    };
                }
            },0);
            String key=name.contains("/")?name.substring(0,name.lastIndexOf('/')+1)+sourceFile[0]:sourceFile[0];
            String source=javaSources.get(key);
            if(source==null)throw new IllegalArgumentException("CHECKPOINT_COMPILED_SOURCE_UNPROVEN:"+name);
            // A stale/shadowed class on another classpath cannot certify this source.
            var loaded=ClassLoader.getSystemResource(name+".class");
            if(loaded==null||!loaded.getProtocol().equals("file")||!Path.of(loaded.toURI()).toRealPath().equals(path.toRealPath()))throw new IllegalArgumentException("CHECKPOINT_PROJECT_CLASS_SHADOWED:"+name);
            byte[] derived=sites[0]>0?writer.toByteArray():original;
            if(sites[0]>0){Files.write(path,derived);managed.add(source);total+=sites[0];}
            hashes.put(exact.relativize(path).toString().replace('\\','/'),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(derived)));
        }
        return new Proof(managed,hashes,total);
    }
}
