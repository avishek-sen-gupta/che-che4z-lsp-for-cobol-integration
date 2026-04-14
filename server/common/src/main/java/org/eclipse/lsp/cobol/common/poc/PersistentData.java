package org.eclipse.lsp.cobol.common.poc;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

}
