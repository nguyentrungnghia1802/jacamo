package org.vnu.sme.goal.verify.conformance.mapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the small line-oriented ".map" format documented in
 * doc/paper/conformance-istar-bpmn2.md, §4.1:
 *
 * <pre>
 *   # comment
 *   actor &lt;istarActorName&gt; -&gt; pool &lt;poolId&gt; [lane &lt;laneId&gt;]
 *   map   &lt;istarElementId&gt;  -&gt; node &lt;bpmnNodeId&gt;
 * </pre>
 *
 * Deliberately not an ANTLR grammar: the format is flat key/value pairs, not worth the
 * overhead of a generated parser.
 */
public final class ConformanceMappingParser {

    private ConformanceMappingParser() {}

    public static ConformanceMapping parse(Path file) throws IOException {
        return parse(Files.readAllLines(file));
    }

    public static ConformanceMapping parse(List<String> lines) {
        List<ElementMapping> elements = new ArrayList<>();
        List<ActorMapping> actors = new ArrayList<>();

        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;

            String[] tok = line.split("\\s+");
            if (tok[0].equals("actor") && tok.length >= 5 && tok[2].equals("->") && tok[3].equals("pool")) {
                String actorName = tok[1];
                String poolId = tok[4];
                String laneId = (tok.length >= 7 && tok[5].equals("lane")) ? tok[6] : null;
                actors.add(new ActorMapping(actorName, poolId, laneId));
            } else if (tok[0].equals("map") && tok.length >= 5 && tok[2].equals("->") && tok[3].equals("node")) {
                elements.add(new ElementMapping(tok[1], tok[4]));
            } else {
                throw new IllegalArgumentException("cannot parse conformance-mapping line: '" + raw + "'");
            }
        }
        return new ConformanceMapping(elements, actors);
    }

    private static String stripComment(String line) {
        int i = line.indexOf('#');
        return i < 0 ? line : line.substring(0, i);
    }
}
