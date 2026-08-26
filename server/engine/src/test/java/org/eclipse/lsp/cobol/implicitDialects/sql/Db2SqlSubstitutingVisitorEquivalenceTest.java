/*
 * Copyright (c) 2024 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.implicitDialects.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.lsp.cobol.common.AnalysisConfig;
import org.eclipse.lsp.cobol.common.SqlDecimalComma;
import org.eclipse.lsp.cobol.common.SqlProcessing;
import org.eclipse.lsp.cobol.common.copybook.CopybookProcessingMode;
import org.eclipse.lsp.cobol.common.copybook.CopybookService;
import org.eclipse.lsp.cobol.common.copybook.SQLBackend;
import org.eclipse.lsp.cobol.common.dialects.CobolLanguageId;
import org.eclipse.lsp.cobol.common.dialects.DialectOutcome;
import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.mapping.ExtendedDocument;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.model.tree.variable.ElementaryNode;
import org.eclipse.lsp.cobol.common.poc.PersistentData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Closes the coverage gap that let {@code Db2SqlSubstitutingVisitor} drift away from {@link
 * Db2SqlVisitor}. {@code DialectService} selects the substituting visitor only when {@code
 * AnalysisConfig.isAddDb2SqlPlaceholder()} is set, so the whole engine suite exercises the original
 * visitor and nothing exercised the fork's copy.
 *
 * <p>Two kinds of assertion live here, and they do different jobs.
 *
 * <ul>
 *   <li><em>Equivalence</em> assertions compare the substituting visitor against the original. They
 *       guard against the subclass drifting again in future.
 *   <li><em>Absolute</em> assertions pin the upstream behaviours the fork's copy had silently lost.
 *       Once the substituting visitor is a record-then-delegate subclass, equivalence holds by
 *       construction, so only these can catch a regression re-introduced into the shared parent.
 * </ul>
 */
@Execution(ExecutionMode.SAME_THREAD)
class Db2SqlSubstitutingVisitorEquivalenceTest {

  private static final String URI = "file:///db2.cbl";

  /** {@code CobolDialect.FILLER}: the zero-width space every dialect blanks its source with. */
  private static final char FILLER = '​';

  /**
   * Each construct below spans one of the upstream behaviours the fork's 510-line copy of {@link
   * Db2SqlVisitor} had lost, so that re-introducing the drift fails a named test instead of only
   * being described in a commit message:
   *
   * <ul>
   *   <li>line 5, {@code 01 WS-MSG PIC X(10).} — a plain COBOL item, not a DB2 construct. Negative
   *       control for {@link #substitutionLeavesNoSqlTextInTheDocument()}: it must survive
   *       untouched, otherwise that test would pass on an all-blank document.
   *   <li>line 6, {@code SQL TYPE IS DBCLOB(10)} — {@code generateVarbinVariables}. Upstream gives
   *       the generated {@code -DATA} item a {@code G(n)} GRAPHIC picture for DBCLOB; the copy
   *       dropped the whole {@code picClause} local and always emitted {@code X(n)}.
   *   <li>line 7, {@code SQL TYPE IS VARBINARY(20)} — {@code visitBinary_host_variable}, the other
   *       entry point into {@code generateVarbinVariables}, which keeps {@code X(n)}.
   *   <li>line 9, a single-line {@code EXEC SQL} — {@code addReplacementContext}. Upstream blanks
   *       each terminal with {@code AntlrRangeUtils.constructRange(Token)}, whose end column is
   *       {@code stopIndex - startIndex + 1}; the copy carried its own {@code constructRange} that
   *       omitted the {@code + 1}, so the last character of every token survived blanking.
   *   <li>lines 10-12, a multi-line {@code EXEC SQL} carrying a {@code --} comment —
   *       {@code preProcessSqlComment}. The copy's {@code findPosition} loop bound was
   *       {@code c <= pos} rather than {@code c < pos}, shifting the comment's start column by one,
   *       which both left the leading {@code -} unblanked and made the substitution grow the line.
   * </ul>
   */
  private static final String TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. DB2TEST.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 WS-MSG PIC X(10).\n"
          + "        01 WS-DOC USAGE IS SQL TYPE IS DBCLOB(10).\n"
          + "        01 WS-BIN USAGE IS SQL TYPE IS VARBINARY(20).\n"
          + "        PROCEDURE DIVISION.\n"
          + "            EXEC SQL SELECT 1 INTO :WS-MSG FROM SYSIBM.SYSDUMMY1 END-EXEC.\n"
          + "            EXEC SQL\n"
          + "               -- pick the row\n"
          + "               SELECT 2 INTO :WS-MSG FROM SYSIBM.SYSDUMMY1 END-EXEC.\n";

  /**
   * The exact blanked form of each {@link #TEXT} line a DB2 construct occupies, keyed by 0-based
   * line index. A golden document rather than a "contains no letters" heuristic, because the
   * cheaper check turned out to be blind to the {@code preProcessSqlComment} drift.
   *
   * <p>Reading the pattern: every terminal token becomes an equally long run of FILLER; the single
   * spaces between tokens survive because they are not part of any token; the trailing COBOL period
   * survives because it is outside the DB2 rule. The {@code .} inside {@code SYSIBM.SYSDUMMY1} does
   * <em>not</em> survive — it is inside a token — hence the run of 16.
   */
  private static final Object[][] EXPECTED_BLANKED_LINES = {
    {5, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(10) + "."},
    {6, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(13) + "."},
    {8, "            " + f(8) + " " + f(6) + " " + f(1) + " " + f(4) + " " + f(7) + " " + f(4) + " "
            + f(16) + " " + f(8) + "."},
    {9, "            " + f(8)},
    {10, "               " + f(15)},
    {11, "               " + f(6) + " " + f(1) + " " + f(4) + " " + f(7) + " " + f(4) + " " + f(16)
            + " " + f(8) + "."},
  };

  /** A run of {@code n} FILLER characters. */
  private static String f(int n) {
    StringBuilder builder = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      builder.append(FILLER);
    }
    return builder.toString();
  }

  /**
   * A decimal comma in a predicate. Upstream forwards {@code AnalysisConfig.sqlDecimalComma} into
   * {@code Db2SqlExecLexer.setSQLDecimalCommaAllowed}, which gates the {@code NUMERICLITERAL}
   * semantic predicate; the copy never called it, so {@code 1,5} always lexed as two integers and
   * the predicate always failed to parse.
   */
  private static final String DECIMAL_COMMA_TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. DB2TEST.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 WS-MSG PIC X(10).\n"
          + "        PROCEDURE DIVISION.\n"
          + "            EXEC SQL SELECT COL1 INTO :WS-MSG FROM TAB1\n"
          + "               WHERE COL2 > 1,5 END-EXEC.\n";

  private CopybookService copybookService;
  private MessageService messageService;

  @BeforeEach
  void setUp() {
    PersistentData.reset();
    copybookService = mock(CopybookService.class, withSettings().lenient());
    messageService = mock(MessageService.class, withSettings().lenient());
    when(messageService.getMessage(anyString())).thenReturn("message");
    when(messageService.getMessage(anyString(), any(Object[].class))).thenReturn("message");
  }

  /**
   * {@code Db2SqlDialect.processText} reads {@code sqlProcessing} and {@code Db2SqlVisitor.parseSQL}
   * reads {@code sqlDecimalCommaAllowed}, so the context needs a real config; neither
   * {@code defaultConfig} nor {@code substitutingDefaultConfig} can express decimal comma ENABLED.
   */
  private static AnalysisConfig config(SqlDecimalComma decimalComma) {
    return new AnalysisConfig(
        CopybookProcessingMode.ENABLED,
        ImmutableList.of(),
        true,
        false,
        SqlProcessing.ENABLED,
        decimalComma,
        ImmutableList.of(),
        ImmutableMap.of("target-sql-backend", new Gson().toJsonTree(SQLBackend.DB2_SERVER)));
  }

  private static DialectProcessingContext freshContext(String text, SqlDecimalComma decimalComma) {
    DialectProcessingContext context =
        DialectProcessingContext.builder()
            .extendedDocument(new ExtendedDocument(text, URI))
            .programDocumentUri(URI)
            .config(config(decimalComma))
            .languageId(CobolLanguageId.COBOL.getId())
            .build();
    context.getExtendedDocument().commitTransformations();
    return context;
  }

  private DialectOutcome run(Db2SqlVisitorBuilder builder, DialectProcessingContext context) {
    return new Db2SqlDialect(copybookService, messageService, builder)
        .processText(context)
        .getResult();
  }

  private List<SyntaxError> errorsOf(
      Db2SqlVisitorBuilder builder, DialectProcessingContext context) {
    return new Db2SqlDialect(copybookService, messageService, builder)
        .processText(context)
        .getErrors();
  }

  /**
   * Whole-subtree dump, not just the top-level nodes: the DBCLOB and VARBINARY drift only shows up
   * in generated grandchildren, and only in their picture clause.
   */
  private static List<String> nodeShapes(List<Node> nodes) {
    return nodes.stream()
        .flatMap(Node::getDepthFirstStream)
        .map(
            n ->
                n.getClass().getSimpleName()
                    + "@"
                    + n.getLocality().getRange()
                    + (n instanceof ElementaryNode
                        ? "[" + ((ElementaryNode) n).getName() + " PIC "
                            + ((ElementaryNode) n).getPicClause() + "]"
                        : ""))
        .sorted()
        .collect(Collectors.toList());
  }

  private static String[] blankedLines(DialectProcessingContext context) {
    // toString() reads baseText, which only picks up substitutions on commit.
    context.getExtendedDocument().commitTransformations();
    return context.getExtendedDocument().toString().split("\n", -1);
  }

  private static String picClauseOf(List<Node> nodes, String name) {
    return nodes.stream()
        .flatMap(Node::getDepthFirstStream)
        .filter(ElementaryNode.class::isInstance)
        .map(ElementaryNode.class::cast)
        .filter(n -> name.equals(n.getName()))
        .map(ElementaryNode::getPicClause)
        .findFirst()
        .orElse(null);
  }

  // ---------------------------------------------------------------- equivalence

  @Test
  void substitutingVisitorProducesTheSameNodesAsTheOriginal() {
    PersistentData.reset();
    List<Node> original =
        run(Db2SqlVisitorBuilder.ORIGINAL, freshContext(TEXT, SqlDecimalComma.DISABLED))
            .getDialectNodes();
    PersistentData.reset();
    List<Node> substituting =
        run(Db2SqlVisitorBuilder.SUBSTITUTING, freshContext(TEXT, SqlDecimalComma.DISABLED))
            .getDialectNodes();

    assertEquals(
        nodeShapes(original),
        nodeShapes(substituting),
        "Reparenting must not change which dialect nodes the DB2 visitor produces");
  }

  @Test
  void substitutingVisitorProducesTheSameErrorsAsTheOriginal() {
    PersistentData.reset();
    List<String> original =
        errorsOf(
                Db2SqlVisitorBuilder.ORIGINAL,
                freshContext(DECIMAL_COMMA_TEXT, SqlDecimalComma.ENABLED))
            .stream()
            .map(SyntaxError::toString)
            .sorted()
            .collect(Collectors.toList());
    PersistentData.reset();
    List<String> substituting =
        errorsOf(
                Db2SqlVisitorBuilder.SUBSTITUTING,
                freshContext(DECIMAL_COMMA_TEXT, SqlDecimalComma.ENABLED))
            .stream()
            .map(SyntaxError::toString)
            .sorted()
            .collect(Collectors.toList());

    assertEquals(
        original, substituting, "Reparenting must not change which errors the DB2 visitor reports");
  }

  @Test
  void substitutingVisitorBlanksTheDocumentJustLikeTheOriginal() {
    PersistentData.reset();
    DialectProcessingContext originalContext = freshContext(TEXT, SqlDecimalComma.DISABLED);
    run(Db2SqlVisitorBuilder.ORIGINAL, originalContext);
    PersistentData.reset();
    DialectProcessingContext substitutingContext = freshContext(TEXT, SqlDecimalComma.DISABLED);
    run(Db2SqlVisitorBuilder.SUBSTITUTING, substitutingContext);

    assertEquals(
        String.join("\n", blankedLines(originalContext)),
        String.join("\n", blankedLines(substitutingContext)),
        "Substitution must stay length-preserving and marker-free, so both documents must match");
  }

  // -------------------------------------------------------------------- absolute

  @Test
  void substitutionLeavesNoSqlTextInTheDocument() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(TEXT, SqlDecimalComma.DISABLED);
    run(Db2SqlVisitorBuilder.SUBSTITUTING, context);
    String[] lines = blankedLines(context);

    // Negative control: a line the DB2 dialect must not touch. Without this, an over-eager
    // substitution that blanked the whole program would satisfy the loop below.
    assertTrue(
        lines[4].contains("WS-MSG"),
        "A plain COBOL data item must survive DB2 substitution untouched, but line 5 was: "
            + lines[4]);

    for (Object[] expected : EXPECTED_BLANKED_LINES) {
      int line = (Integer) expected[0];
      assertEquals(
          expected[1],
          lines[line],
          "Line "
              + (line + 1)
              + " of the substituted document must be blanked exactly as upstream blanks it "
              + "(fillers shown as zero-width, so compare the lengths reported above)");
    }
  }

  @Test
  void dbclobHostVariableGetsAGraphicPictureClause() {
    PersistentData.reset();
    List<Node> nodes =
        run(Db2SqlVisitorBuilder.SUBSTITUTING, freshContext(TEXT, SqlDecimalComma.DISABLED))
            .getDialectNodes();

    assertEquals(
        "G(10)",
        picClauseOf(nodes, "WS-DOC-DATA"),
        "DBCLOB is a double-byte type, so the generated -DATA item must get a GRAPHIC picture");
    assertEquals(
        "X(20)",
        picClauseOf(nodes, "WS-BIN-TEXT"),
        "VARBINARY stays single-byte, so the generated -TEXT item must keep an alphanumeric picture");
  }

  @Test
  void decimalCommaSettingReachesTheSqlLexer() {
    PersistentData.reset();
    List<SyntaxError> allowed =
        errorsOf(
            Db2SqlVisitorBuilder.SUBSTITUTING,
            freshContext(DECIMAL_COMMA_TEXT, SqlDecimalComma.ENABLED));
    PersistentData.reset();
    List<SyntaxError> forbidden =
        errorsOf(
            Db2SqlVisitorBuilder.SUBSTITUTING,
            freshContext(DECIMAL_COMMA_TEXT, SqlDecimalComma.DISABLED));

    assertTrue(
        allowed.isEmpty(),
        "With SqlDecimalComma.ENABLED, '1,5' must lex as one numeric literal, but got: " + allowed);
    assertFalse(
        forbidden.isEmpty(),
        "With SqlDecimalComma.DISABLED, '1,5' must not parse as a predicate operand");
  }

  // --------------------------------------------------------------- fragments

  @Test
  void substitutingVisitorRecordsOneFragmentPerSubstitutedConstruct() {
    PersistentData.reset();
    run(Db2SqlVisitorBuilder.SUBSTITUTING, freshContext(TEXT, SqlDecimalComma.DISABLED));

    // Exact, not a lower bound: an exact count is what makes a lost anchor fail loudly. Both
    // EXEC SQL blocks and both host-variable declarations are blanked out of the extended
    // document, so smojol needs a fragment for each of the four.
    assertEquals(
        4,
        PersistentData.fragmentCount(),
        "Each substituted DB2 construct must record exactly one positional fragment");

    // Coordinates are ANTLR's: 1-based line, 0-based column.
    assertFragmentAt(6, 8, "DBCLOB");
    assertFragmentAt(7, 8, "VARBINARY");
    assertFragmentAt(9, 12, "SELECT");
    assertFragmentAt(10, 12, "SELECT");
  }

  private static void assertFragmentAt(int line, int charPos, String expectedToken) {
    PersistentData.Fragment fragment = PersistentData.fragmentAt(line, charPos);
    assertTrue(
        fragment != null && fragment.tree.getText().toUpperCase().contains(expectedToken),
        "The fragment recorded at line "
            + line
            + " must be the DB2 parse tree containing "
            + expectedToken);
  }

  @Test
  void originalVisitorRecordsNoFragments() {
    PersistentData.reset();
    run(Db2SqlVisitorBuilder.ORIGINAL, freshContext(TEXT, SqlDecimalComma.DISABLED));

    assertEquals(
        0,
        PersistentData.fragmentCount(),
        "The original visitor must not record fragments — smojol's flag is what turns this on");
  }
}
