package org.eclipse.lsp.cobol.common.poc;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// TODO: Inject this. This is super ugly!
/**
 * NOT THREAD-SAFE. This class uses unprotected static mutable state (counter, trees list).
 * smojol parses files sequentially — this is a deliberate architectural constraint.
 * Do NOT parallelize calls to ParsePipeline in the same JVM without replacing this class
 * with a scoped, thread-local equivalent first.
 *
 * @see <a href="../../../../../../../../../../COBOL-LSP-INTEGRATION.md">COBOL-LSP-INTEGRATION.md</a>
 */
public class PersistentData {
    /**
     * A dialect fragment that was blanked out of the extended document, together with the dialect
     * parse tree that produced it.
     *
     * <p>Coordinates are ANTLR token coordinates: {@code startLine}/{@code endLine} are 1-based
     * ({@code Token.getLine()}), {@code startChar} is 0-based
     * ({@code Token.getCharPositionInLine()}). They are positions in the <em>extended
     * document</em>, which every dialect parses in full, so they are directly comparable with
     * positions from the later COBOL parse of the same document.
     */
    public static final class Fragment {
        public final int startLine;
        public final int startChar;
        public final int endLine;
        public final LocalisedDialect dialect;
        public final ParseTree tree;

        private Fragment(int startLine, int startChar, int endLine, LocalisedDialect dialect, ParseTree tree) {
            this.startLine = startLine;
            this.startChar = startChar;
            this.endLine = endLine;
            this.dialect = dialect;
            this.tree = tree;
        }

        /**
         * Whether the given position falls inside this fragment. The check is a range, not an
         * equality test, because IDMS's {@code _IF_ } prefix is the one substitution that is not
         * length-preserving: it shifts the filler run 5 columns to the right of the recorded start.
         */
        public boolean covers(int line, int charPos) {
            if (line < startLine || line > endLine) return false;
            if (line == startLine) return charPos >= startChar;
            return true;
        }
    }

    private static final List<Fragment> fragments = new ArrayList<>();
    private static final Set<Fragment> claimed = Collections.newSetFromMap(new IdentityHashMap<>());

    public static int counter = 0;

    public static String next() {
        counter ++;
        return String.valueOf(counter);
    }
    private static AnnotatedParserRuleContext tree;
    private static List<AnnotatedParserRuleContext> trees = new ArrayList<>();

    public static void addDialectTree(AnnotatedParserRuleContext tree) {
        PersistentData.tree = tree;
        trees.add(tree);
    }

    public static ParseTree getDialectNode(String displayOperand) {
        for (AnnotatedParserRuleContext tree : trees) {
            ParseTree dialectNode = getDialectNode(displayOperand, tree);
            if (dialectNode == null) continue;
            return dialectNode;
        }
        return null;
    }

    public static ParseTree getDialectNode(String displayOperand, AnnotatedParserRuleContext node) {
        if (node.customData.get(displayOperand) != null)
            return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChild(i) instanceof TerminalNode) continue;
            ParseTree result = getDialectNode(displayOperand, (AnnotatedParserRuleContext) node.getChild(i));
            if (result != null) return result;
        }

        return null;
    }

    public static LocalisedDialect dialect(String displayOperand) {
        return ((AnnotatedParserRuleContext) Objects.requireNonNull(getDialectNode(displayOperand))).dialect;
    }

    /** Returns the number of IDMS parse trees currently registered. */
    public static int treeCount() {
        return trees.size();
    }

    /** Records the region {@code ctx} occupies in the extended document, and its parse tree. */
    public static void record(ParserRuleContext ctx, LocalisedDialect dialect) {
        int startLine = ctx.getStart().getLine();
        int startChar = ctx.getStart().getCharPositionInLine();
        int endLine = ctx.getStop() != null ? ctx.getStop().getLine() : startLine;
        fragments.add(new Fragment(startLine, startChar, endLine, dialect, ctx));
    }

    /** Non-consuming lookup. Returns {@code null} when no fragment covers the position. */
    public static Fragment fragmentAt(int line, int charPos) {
        for (Fragment fragment : fragments) {
            if (fragment.covers(line, charPos)) return fragment;
        }
        return null;
    }

    /** Whether any fragment — claimed or not — covers the position. */
    public static boolean isCovered(int line, int charPos) {
        return fragmentAt(line, charPos) != null;
    }

    /**
     * Consuming lookup: returns the earliest unclaimed fragment covering the position and marks it
     * claimed, so a fragment is grafted at most once. Returns {@code null} when none is left.
     */
    public static Fragment claim(int line, int charPos) {
        for (Fragment fragment : fragments) {
            if (claimed.contains(fragment)) continue;
            if (fragment.covers(line, charPos)) {
                claimed.add(fragment);
                return fragment;
            }
        }
        return null;
    }

    /** Number of fragments recorded since the last {@link #reset()}. */
    public static int fragmentCount() {
        return fragments.size();
    }

    /**
     * Resets all static state. Intended for use in tests only.
     * Must be called in {@code @BeforeEach} when tests need to assert exact extraction counts
     * or IDs, since the counter and trees list accumulate across tests in the same JVM.
     */
    public static void reset() {
        counter = 0;
        tree = null;
        trees.clear();
        fragments.clear();
        claimed.clear();
    }

}
