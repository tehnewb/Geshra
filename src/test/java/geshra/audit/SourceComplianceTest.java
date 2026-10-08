package geshra.audit;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces mechanically verifiable AGENTS.md source rules across production code, tests,
 * benchmarks, and the consumer example. Uses the JDK parser without adding a dependency or
 * loading application classes. Generated build output and external assets are outside this scan.
 * Architectural cohesion, comment accuracy, and performance still require human review.
 */
class SourceComplianceTest {

    /**
     * Parses every maintained Java source and reports rule failures with file and line locations.
     *
     * @throws Exception if maintained sources cannot be read or parsed
     */
    @Test
    void maintainedJavaSourcesFollowTheProjectRules() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler)
                .as("Source auditing requires a JDK")
                .isNotNull();
        List<File> files = new ArrayList<>();
        for (String directory : List.of("src", "examples/spring-app/src")) {
            try (Stream<Path> paths = Files.walk(Path.of(directory))) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> files.add(path.toFile()));
            }
        }

        List<String> violations = new ArrayList<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null, manager.getJavaFileObjectsFromFiles(files));
            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                String source = unit.getSourceFile()
                        .getCharContent(true)
                        .toString();
                new JavaSourceAudit(trees, unit, source, violations)
                        .scan(unit, null);
            }
        }
        assertThat(violations)
                .as("AGENTS.md source violations")
                .isEmpty();
    }
}
