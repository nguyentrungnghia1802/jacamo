package org.jacamo.checkpoint.runtime;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.*;

/** Painting may remain live during a hold; arbitrary EDT callbacks may not mutate domain state.
 * This admits typed JDK presentation receivers only, not source class/method names. */
final class GuiCallbackAdmission {
    static List<SourceAdmission.Rejection> inspect(Path root,Collection<Path> sources)throws java.io.IOException {
        var java=sources.stream().filter(p->p.toString().endsWith(".java")).toList();
        if(java.isEmpty())return List.of();
        var compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)return List.of(new SourceAdmission.Rejection("project","GUI_SOURCE_ANALYSIS_REQUIRES_JDK"));
        var rejected=new ArrayList<SourceAdmission.Rejection>();
        try(var files=compiler.getStandardFileManager(null,null,null)){
            var task=(JavacTask)compiler.getTask(null,files,d->{},List.of("-proc:none"),null,files.getJavaFileObjectsFromPaths(java));
            for(var unit:task.parse()){
                String path=root.relativize(Path.of(unit.getSourceFile().toUri()).toRealPath()).toString().replace('\\','/');
                var imports=new HashMap<String,String>();for(var item:unit.getImports()){
                    String type=item.getQualifiedIdentifier().toString();imports.put(type.substring(type.lastIndexOf('.')+1),type);
                }
                new TreePathScanner<Void,Map<String,String>>(){
                    @Override public Void visitClass(ClassTree type,Map<String,String> outer){
                        var fields=new HashMap<String,String>();
                        for(var member:type.getMembers())if(member instanceof VariableTree field)fields.put(field.getName().toString(),field.getType().toString());
                        return super.visitClass(type,fields);
                    }
                    @Override public Void visitMethod(MethodTree method,Map<String,String> fields){
                        var variables=new HashMap<>(fields==null?Map.<String,String>of():fields);
                        for(var parameter:method.getParameters())variables.put(parameter.getName().toString(),parameter.getType().toString());
                        return super.visitMethod(method,variables);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree call,Map<String,String> variables){
                        String target=call.getMethodSelect().toString();
                        if(target.matches(".*\\.add(?:Action|Mouse|MouseMotion|Key|Window|Change|Item|Focus|PropertyChange)Listener")
                                ||target.startsWith("EventQueue.")||target.startsWith("java.awt.EventQueue."))
                            rejected.add(new SourceAdmission.Rejection(path,"UNADMITTED_GUI_EVENT_PRODUCER"));
                        if(target.endsWith(".invokeLater")||target.endsWith(".invokeAndWait")){
                            for(var argument:call.getArguments())if(!presentation(argument,variables==null?Map.of():variables,imports))
                                rejected.add(new SourceAdmission.Rejection(path,"GUI_CALLBACK_DOMAIN_ACCESS_UNPROVEN"));
                        }
                        return super.visitMethodInvocation(call,variables);
                    }
                }.scan(unit,Map.of());
            }
        }
        return rejected;
    }
    private static boolean presentation(Tree body,Map<String,String> fields,Map<String,String> imports){
        if(!(body instanceof NewClassTree||body instanceof LambdaExpressionTree))return false;
        boolean[] valid={true};var locals=new HashSet<String>();
        new TreeScanner<Void,Void>(){
            String root(ExpressionTree expression){
                if(expression instanceof MemberSelectTree selected)return root(selected.getExpression());
                if(expression instanceof ArrayAccessTree array)return root(array.getExpression());
                return expression instanceof IdentifierTree id?id.getName().toString():"";
            }
            boolean receiver(ExpressionTree expression){
                String name=root(expression),type=fields.getOrDefault(name,imports.getOrDefault(name,""));
                type=type.replace("[]","");type=imports.getOrDefault(type,type);
                return type.startsWith("javax.swing.")||type.startsWith("java.awt.");
            }
            @Override public Void visitVariable(VariableTree variable,Void ignored){locals.add(variable.getName().toString());return super.visitVariable(variable,ignored);}
            @Override public Void visitMethodInvocation(MethodInvocationTree call,Void ignored){
                if(!(call.getMethodSelect() instanceof MemberSelectTree member)||!receiver(member.getExpression()))valid[0]=false;
                return super.visitMethodInvocation(call,ignored);
            }
            @Override public Void visitAssignment(AssignmentTree assignment,Void ignored){
                if(!locals.contains(root(assignment.getVariable())))valid[0]=false;return super.visitAssignment(assignment,ignored);
            }
            @Override public Void visitCompoundAssignment(CompoundAssignmentTree assignment,Void ignored){
                if(!locals.contains(root(assignment.getVariable())))valid[0]=false;return super.visitCompoundAssignment(assignment,ignored);
            }
            @Override public Void visitUnary(UnaryTree unary,Void ignored){
                if(Set.of(Tree.Kind.PREFIX_INCREMENT,Tree.Kind.POSTFIX_INCREMENT,Tree.Kind.PREFIX_DECREMENT,Tree.Kind.POSTFIX_DECREMENT).contains(unary.getKind())&&!locals.contains(root(unary.getExpression())))valid[0]=false;
                return super.visitUnary(unary,ignored);
            }
        }.scan(body,null);
        return valid[0];
    }
}
