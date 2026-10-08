package geshra.audit;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Visits one parsed source file and records structural, documentation, and formatting violations.
 * All offsets come from the JDK parser, so declarations inside strings and examples are not mistaken
 * for executable source. Record components are documented by the record API rather than treated as
 * explicit fields. Qualified Spring Component annotations are allowed when the UI Component name
 * is already imported, matching AGENTS.md's unavoidable type-name collision exception.
 */
final class JavaSourceAudit extends TreePathScanner<Void, Void> {

    /**
     * Recognizes package-qualified type references rather than ordinary variable member access.
     */
    private static final Pattern QUALIFIED_TYPE = Pattern.compile("(?:java|javax|org|com|io|geshra)(?:\\.[a-z_][\\w]*)+\\.[A-Z][\\w]*");

    /**
     * Identifies prohibited one-line documentation and implementation block comments.
     */
    private static final Pattern SINGLE_LINE_BLOCK = Pattern.compile("(?m)^\\s*/\\*\\*?[^\\r\\n]*\\*/");

    private final Trees trees; // Parser documentation lookup for the current declaration.
    private final SourcePositions positions; // Source offsets in the compiler's UTF-16 coordinate system.
    private final CompilationUnitTree unit; // Current source file and its line map.
    private final String source; // Unmodified source used to inspect declaration formatting.
    private final List<String> violations; // Shared findings accumulated by the owning test.

    /**
     * Creates a scanner for one maintained Java source.
     *
     * @param trees compiler tree and documentation utilities
     * @param unit current compilation unit
     * @param source source text matching compiler offsets
     * @param violations destination for file-and-line findings
     */
    JavaSourceAudit(Trees trees, CompilationUnitTree unit, String source, List<String> violations) {
        this.trees = trees;
        this.positions = trees.getSourcePositions();
        this.unit = unit;
        this.source = source;
        this.violations = violations;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree compilationUnit, Void unused) {
        if (SINGLE_LINE_BLOCK.matcher(source).find())
            report(compilationUnit, "One-line block comments are prohibited");
        return super.visitCompilationUnit(compilationUnit, unused);
    }

    @Override
    public Void visitClass(ClassTree type, Void unused) {
        if (!type.getSimpleName().isEmpty()) {
            requireDocumentation(type);
            if (!(getCurrentPath().getParentPath().getLeaf() instanceof CompilationUnitTree))
                report(type, "Named types must have their own source files");
            int headerStart = Math.max(start(type), end(type.getModifiers()));
            int body = source.indexOf('{', headerStart);
            if (body >= 0 && source.substring(headerStart, body).strip().contains("\n"))
                report(type, "Type declarations must remain on one line");
        }
        boolean behaviorSeen = false;
        int previousFieldStart = -1;
        for (Tree member : type.getMembers()) {
            if (member instanceof MethodTree) behaviorSeen = true;
            if (member instanceof VariableTree) {
                if (behaviorSeen) report(member, "Fields must precede constructors and methods");
                if (start(member) == previousFieldStart) report(member, "Each field requires its own declaration");
                previousFieldStart = start(member);
            }
        }
        return super.visitClass(type, unused);
    }

    @Override
    public Void visitMethod(MethodTree method, Void unused) {
        boolean override = false;
        for (AnnotationTree annotation : method.getModifiers().getAnnotations())
            if (annotation.getAnnotationType().toString().equals("Override")) override = true;
        if (!override) requireDocumentation(method);
        if (!method.getParameters().isEmpty()) {
            VariableTree first = method.getParameters()
                    .getFirst();
            VariableTree last = method.getParameters()
                    .getLast();
            if (slice(start(first), end(last)).contains("\n"))
                report(method, "Method parameters must remain on one line");
        }
        if (method.getModifiers().getFlags().contains(Modifier.STATIC) && method.getBody() != null) {
            String body = slice(start(method.getBody()), end(method.getBody()));
            if (!body.contains("/*\n") && !body.contains("/*\r\n"))
                report(method, "Static methods require an internal multiline block comment");
        }
        return super.visitMethod(method, unused);
    }

    @Override
    public Void visitVariable(VariableTree field, Void unused) {
        if (!(getCurrentPath().getParentPath().getLeaf() instanceof ClassTree))
            return super.visitVariable(field, unused);
        String declaration = slice(start(field), end(field));
        boolean constant = declaration.startsWith(field.getName().toString());
        if (!constant && !declaration.endsWith(";")) return super.visitVariable(field, unused);
        boolean isStatic = field.getModifiers()
                .getFlags()
                .contains(Modifier.STATIC);
        String trailing = slice(end(field), lineEnd(end(field)));
        if (isStatic) {
            requireDocumentation(field);
            if (trailing.contains("//")) report(field, "Static fields must not have trailing documentation");
        } else {
            if (trees.getDocComment(getCurrentPath()) != null)
                report(field, "Instance fields must not use Javadoc");
            if (!trailing.matches("\\s*//\\s*\\S.*"))
                report(field, "Instance fields require a right-side comment");
        }
        if (constant && !slice(source.lastIndexOf('\n', start(field)) + 1, start(field)).isBlank())
            report(field, "Enum constants must each begin on their own line");
        return super.visitVariable(field, unused);
    }

    @Override
    public Void visitAnnotation(AnnotationTree annotation, Void unused) {
        TreePath parent = getCurrentPath()
                .getParentPath();
        if (parent != null && parent.getParentPath() != null) {
            Tree annotated = parent.getParentPath()
                    .getLeaf();
            boolean parameter = annotated instanceof VariableTree && !(parent.getParentPath().getParentPath().getLeaf() instanceof ClassTree);
            if (!parameter && !slice(end(annotation), lineEnd(end(annotation))).isBlank())
                report(annotation, "Annotations must occupy their own lines");
        }
        return super.visitAnnotation(annotation, unused);
    }

    @Override
    public Void visitCatch(CatchTree caught, Void unused) {
        if (caught.getBlock().getStatements().isEmpty()) report(caught, "Empty catch blocks are prohibited");
        return super.visitCatch(caught, unused);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree member, Void unused) {
        String reference = slice(start(member), end(member));
        if (QUALIFIED_TYPE.matcher(reference).matches() && !insideImport()) {
            String simpleName = reference.substring(reference.lastIndexOf('.') + 1);
            boolean collision = false;
            for (ImportTree imported : unit.getImports()) {
                String importedName = imported.getQualifiedIdentifier()
                        .toString();
                if (importedName.endsWith("." + simpleName) && !importedName.equals(reference)) collision = true;
            }
            if (!collision) report(member, "Import types instead of qualifying them in source");
        }
        return super.visitMemberSelect(member, unused);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
        Tree parent = getCurrentPath()
                .getParentPath()
                .getLeaf();
        if (!(parent instanceof MemberSelectTree) && invocation.getMethodSelect() instanceof MemberSelectTree member) {
            boolean standalone = parent.getKind() == Tree.Kind.VARIABLE || parent.getKind() == Tree.Kind.ASSIGNMENT || parent.getKind() == Tree.Kind.RETURN || parent.getKind() == Tree.Kind.EXPRESSION_STATEMENT;
            ExpressionTree current = invocation;
            while (current instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select) {
                ExpressionTree receiver = select.getExpression();
                if (receiver instanceof MethodInvocationTree || receiver instanceof NewClassTree) {
                    String gap = slice(end(receiver), end(select));
                    if (standalone != gap.contains("\n"))
                        report(call, standalone ? "Standalone chains must be vertical" : "Parenthesized chains must remain compact");
                }
                current = receiver;
            }
        }
        return super.visitMethodInvocation(invocation, unused);
    }

    /**
     * Checks declaration documentation and rejects single-line Javadocs.
     *
     * @param declaration declaration requiring a useful multiline comment
     */
    private void requireDocumentation(Tree declaration) {
        String doc = trees.getDocComment(getCurrentPath());
        if (doc == null || doc.isBlank()) {
            report(declaration, "Declaration requires Javadoc");
            return;
        }
        int opening = source.lastIndexOf("/**", start(declaration));
        int closing = opening < 0 ? -1 : source.indexOf("*/", opening);
        if (closing >= 0 && !slice(opening, closing).contains("\n"))
            report(declaration, "Single-line Javadocs are prohibited");
    }

    /**
     * Reports whether a member reference belongs to an import declaration.
     *
     * @return whether the current path is inside an import
     */
    private boolean insideImport() {
        for (TreePath path = getCurrentPath(); path != null; path = path.getParentPath())
            if (path.getLeaf() instanceof ImportTree) return true;
        return false;
    }

    /**
     * Returns a parser offset for the first character of a tree.
     *
     * @param tree parsed node
     * @return source start offset
     */
    private int start(Tree tree) {
        return (int) positions.getStartPosition(unit, tree);
    }

    /**
     * Returns the source offset immediately after a tree.
     *
     * @param tree parsed node
     * @return exclusive source end offset
     */
    private int end(Tree tree) {
        return (int) positions.getEndPosition(unit, tree);
    }

    /**
     * Finds the end of the source line containing an offset.
     *
     * @param offset source position
     * @return end of line content, excluding either Windows or Unix newline characters
     */
    private int lineEnd(int offset) {
        int newline = source.indexOf('\n', offset);
        int end = newline < 0 ? source.length() : newline;
        return end > offset && source.charAt(end - 1) == '\r' ? end - 1 : end;
    }

    /**
     * Extracts a compiler-coordinate source range.
     *
     * @param start inclusive source offset
     * @param end exclusive source offset
     * @return source text in the range
     */
    private String slice(int start, int end) {
        return source.substring(start, end);
    }

    /**
     * Appends an actionable file-and-line rule violation.
     *
     * @param tree offending parsed node
     * @param message violated source rule
     */
    private void report(Tree tree, String message) {
        violations.add(unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(start(tree)) + ": " + message);
    }
}
