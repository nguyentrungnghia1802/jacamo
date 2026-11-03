package org.vnu.sme.goal.dsl.csl.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.vnu.sme.goal.dsl.csl.ast.CslModelCS;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.trace.cslTrace.nativecsl.NativeOclEvaluator;

public final class CslCompiler {
    public record Result(CslModelCS ast, CslModel model, List<String> errors) {
        public Result {
            errors = List.copyOf(errors);
        }

        public boolean ok() {
            return errors.isEmpty() && ast != null && model != null;
        }
    }

    private CslCompiler() {}

    public static Result compile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        return compileStream(CharStreams.fromPath(file), file.toString());
    }

    public static Result compile(String source) {
        return compile(source, "<memory>");
    }

    public static Result compile(String source, String sourceName) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceName, "sourceName");
        return compileStream(CharStreams.fromString(source), sourceName);
    }

    private static Result compileStream(CharStream input, String sourceName) {
        List<String> errors = new ArrayList<>();
        CSLLexer lexer = new CSLLexer(input);
        CSLParser parser = new CSLParser(new CommonTokenStream(lexer));

        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        ANTLRErrorListener listener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int column,
                                    String message, RecognitionException exception) {
                errors.add(format(sourceName, line, column, "syntax", message));
            }
        };
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        CSLParser.ModelContext parseTree = parser.model();
        if (!errors.isEmpty()) return new Result(null, null, errors);

        CslModelCS ast = new CslBuildingVisitor().visitModel(parseTree);
        CslModelFactory.Result factory = CslModelFactory.create(ast);
        for (CslModelFactory.SemanticError error : factory.errors()) {
            errors.add(format(sourceName, error.location().line(), error.location().column(),
                    "semantic", error.message()));
        }
        for (var invariant : ast.invariants()) {
            try {
                if (ast.version().startsWith("v4.")) {
                    CslCoreOclSyntax.validate(invariant.expression());
                } else {
                    NativeOclEvaluator.validate(invariant.expression());
                }
            } catch (IllegalArgumentException ex) {
                errors.add(format(sourceName, invariant.location().line(), invariant.location().column(),
                        "OCL", ex.getMessage()));
            }
        }
        return new Result(ast, factory.model(), errors);
    }

    private static String format(String sourceName, int line, int column, String phase, String message) {
        return sourceName + ":" + line + ":" + column + ": " + phase + ": " + message;
    }
}
