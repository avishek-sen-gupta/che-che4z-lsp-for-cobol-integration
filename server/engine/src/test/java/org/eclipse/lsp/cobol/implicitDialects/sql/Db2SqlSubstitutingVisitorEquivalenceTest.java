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
   * Spans two things at once, one construct per line.
   *
   * <p>First, the upstream behaviours the fork's 510-line copy of {@link Db2SqlVisitor} had lost, so
   * that re-introducing the drift fails a named test instead of only being described in a commit
   * message:
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
   *   <li>line 16, a single-line {@code EXEC SQL} — {@code addReplacementContext}. Upstream blanks
   *       each terminal with {@code AntlrRangeUtils.constructRange(Token)}, whose end column is
   *       {@code stopIndex - startIndex + 1}; the copy carried its own {@code constructRange} that
   *       omitted the {@code + 1}, so the last character of every token survived blanking.
   *   <li>lines 17-19, a multi-line {@code EXEC SQL} carrying a {@code --} comment —
   *       {@code preProcessSqlComment}. The copy's {@code findPosition} loop bound was
   *       {@code c <= pos} rather than {@code c < pos}, shifting the comment's start column by one,
   *       which both left the leading {@code -} unblanked and made the substitution grow the line.
   * </ul>
   *
   * <p>Second, every rule {@code Db2SqlSubstitutingVisitor} overrides, so that deleting any single
   * override loses a named fragment anchor rather than passing silently. One line per override:
   *
   * <ul>
   *   <li>line 6 — {@code visitLob_host_variables}
   *   <li>line 7 — {@code visitBinary_host_variable}
   *   <li>line 8 — {@code visitRowid_host_variables}
   *   <li>line 9 — {@code visitResult_set_locator_variable} (grammar requires level 01)
   *   <li>line 10 — {@code visitTableLocators_variable}
   *   <li>line 11 — {@code visitLob_xml_host_variables}
   *   <li>line 12 — {@code visitBinary_host_variable_array}. The grammar spells this one without the
   *       optional {@code USAGE IS} and with a mandatory {@code OCCURS}; a level of 01 would be
   *       taken by {@code binary_host_variable}, which is why this one is 02.
   *   <li>line 13 — {@code visitRowid_host_variables_arrays} ({@code dbs_host_var_levels_arrays}
   *       validates the level into 2..48, so 01 is not allowed here)
   *   <li>line 14 — {@code visitLob_host_variables_arrays}, same level constraint
   *   <li>lines 16 and 17 — {@code visitExecRule}, twice
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
          + "        01 WS-RID USAGE IS SQL TYPE IS ROWID.\n"
          + "        01 WS-RSL USAGE IS SQL TYPE IS RESULT-SET-LOCATOR VARYING.\n"
          + "        01 WS-TLOC USAGE IS SQL TYPE IS TABLE LIKE MYTAB AS LOCATOR.\n"
          + "        01 WS-XML USAGE IS SQL TYPE IS XML AS CLOB(200).\n"
          + "        02 WS-BINA SQL TYPE IS VARBINARY(20) OCCURS 5 TIMES.\n"
          + "        03 WS-RIDA USAGE IS SQL TYPE IS ROWID OCCURS 5 TIMES.\n"
          + "        04 WS-CLOBA USAGE IS SQL TYPE IS CLOB(100) OCCURS 5 TIMES.\n"
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
    // 01 WS-DOC USAGE IS SQL TYPE IS DBCLOB(10).
    {5, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(10) + "."},
    // 01 WS-BIN USAGE IS SQL TYPE IS VARBINARY(20).
    {6, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(13) + "."},
    // 01 WS-RID USAGE IS SQL TYPE IS ROWID.
    {7, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(5) + "."},
    // 01 WS-RSL USAGE IS SQL TYPE IS RESULT-SET-LOCATOR VARYING.
    {8, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(18) + " " + f(7) + "."},
    // 01 WS-TLOC USAGE IS SQL TYPE IS TABLE LIKE MYTAB AS LOCATOR.
    {9, "        " + f(2) + " " + f(7) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(5) + " " + f(4) + " " + f(5) + " " + f(2) + " " + f(7) + "."},
    // 01 WS-XML USAGE IS SQL TYPE IS XML AS CLOB(200).
    {10, "        " + f(2) + " " + f(6) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(3) + " " + f(2) + " " + f(9) + "."},
    // 02 WS-BINA SQL TYPE IS VARBINARY(20) OCCURS 5 TIMES.
    {11, "        " + f(2) + " " + f(7) + " " + f(3) + " " + f(4) + " " + f(2) + " " + f(13) + " "
            + f(6) + " " + f(1) + " " + f(5) + "."},
    // 03 WS-RIDA USAGE IS SQL TYPE IS ROWID OCCURS 5 TIMES.
    {12, "        " + f(2) + " " + f(7) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(5) + " " + f(6) + " " + f(1) + " " + f(5) + "."},
    // 04 WS-CLOBA USAGE IS SQL TYPE IS CLOB(100) OCCURS 5 TIMES.
    {13, "        " + f(2) + " " + f(8) + " " + f(5) + " " + f(2) + " " + f(3) + " " + f(4) + " "
            + f(2) + " " + f(9) + " " + f(6) + " " + f(1) + " " + f(5) + "."},
    // EXEC SQL SELECT 1 INTO :WS-MSG FROM SYSIBM.SYSDUMMY1 END-EXEC.
    {15, "            " + f(8) + " " + f(6) + " " + f(1) + " " + f(4) + " " + f(7) + " " + f(4) + " "
            + f(16) + " " + f(8) + "."},
    // EXEC SQL
    {16, "            " + f(8)},
    //    -- pick the row
    {17, "               " + f(15)},
    //    SELECT 2 INTO :WS-MSG FROM SYSIBM.SYSDUMMY1 END-EXEC.
    {18, "               " + f(6) + " " + f(1) + " " + f(4) + " " + f(7) + " " + f(4) + " " + f(16)
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
   * FILLER is zero-width, so a raw assertion message would show two seemingly identical strings.
   * Renders each filler run as {@code ~} so a mismatch reads as a diff of token lengths.
   */
  private static String render(String line) {
    return line.replace(FILLER, '~');
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
   * The ordinary path. Copybook processing must be ENABLED or {@code Db2SqlDialect} dereferences a
   * null copybook config; {@code defaultConfig} already pins {@code SqlProcessing.ENABLED} and
   * {@code SqlDecimalComma.DISABLED}, which is exactly what every test but the decimal-comma pair
   * wants.
   */
  private static DialectProcessingContext freshContext(String text) {
    return contextWith(text, AnalysisConfig.defaultConfig(CopybookProcessingMode.ENABLED));
  }

  /**
   * Only the decimal-comma variant needs the raw constructor: no factory on {@link AnalysisConfig}
   * can express {@code SqlDecimalComma.ENABLED}, and {@code Db2SqlVisitor.parseSQL} reads it to
   * decide whether {@code Db2SqlExecLexer}'s {@code NUMERICLITERAL} predicate holds.
   */
  private static DialectProcessingContext freshContext(String text, SqlDecimalComma decimalComma) {
    return contextWith(
        text,
        new AnalysisConfig(
            CopybookProcessingMode.ENABLED,
            ImmutableList.of(),
            true,
            false,
            SqlProcessing.ENABLED,
            decimalComma,
            ImmutableList.of(),
            ImmutableMap.of("target-sql-backend", new Gson().toJsonTree(SQLBackend.DB2_SERVER))));
  }

  private static DialectProcessingContext contextWith(String text, AnalysisConfig config) {
    DialectProcessingContext context =
        DialectProcessingContext.builder()
            .extendedDocument(new ExtendedDocument(text, URI))
            .programDocumentUri(URI)
            .config(config)
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
        run(Db2SqlVisitorBuilder.ORIGINAL, freshContext(TEXT))
            .getDialectNodes();
    PersistentData.reset();
    List<Node> substituting =
        run(Db2SqlVisitorBuilder.SUBSTITUTING, freshContext(TEXT))
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
    DialectProcessingContext originalContext = freshContext(TEXT);
    run(Db2SqlVisitorBuilder.ORIGINAL, originalContext);
    PersistentData.reset();
    DialectProcessingContext substitutingContext = freshContext(TEXT);
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
    DialectProcessingContext context = freshContext(TEXT);
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
          render((String) expected[1]),
          render(lines[line]),
          "Line "
              + (line + 1)
              + " of the substituted document must be blanked exactly as upstream blanks it "
              + "(each ~ is one FILLER character)");
    }
  }

  @Test
  void dbclobHostVariableGetsAGraphicPictureClause() {
    PersistentData.reset();
    List<Node> nodes =
        run(Db2SqlVisitorBuilder.SUBSTITUTING, freshContext(TEXT))
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
    run(Db2SqlVisitorBuilder.SUBSTITUTING, freshContext(TEXT));

    // One anchor per override, checked before the count so that a deleted override fails with the
    // line and the method name rather than with an off-by-one total.
    // Coordinates are ANTLR's: 1-based line, 0-based column.
    assertFragmentAt(6, 8, "DBCLOB", "visitLob_host_variables");
    assertFragmentAt(7, 8, "VARBINARY", "visitBinary_host_variable");
    assertFragmentAt(8, 8, "ROWID", "visitRowid_host_variables");
    assertFragmentAt(9, 8, "RESULT-SET-LOCATOR", "visitResult_set_locator_variable");
    assertFragmentAt(10, 8, "TABLELIKE", "visitTableLocators_variable");
    assertFragmentAt(11, 8, "XML", "visitLob_xml_host_variables");
    assertFragmentAt(12, 8, "OCCURS5TIMES", "visitBinary_host_variable_array");
    assertFragmentAt(13, 8, "ROWIDOCCURS", "visitRowid_host_variables_arrays");
    assertFragmentAt(14, 8, "CLOB(100)", "visitLob_host_variables_arrays");
    assertFragmentAt(16, 12, "SELECT", "visitExecRule");
    assertFragmentAt(17, 12, "SELECT", "visitExecRule");

    // Exact, not a lower bound: this is what catches a *spurious* extra fragment, which no
    // per-line anchor can see. Must equal the number of overrides TEXT reaches.
    assertEquals(
        11,
        PersistentData.fragmentCount(),
        "Each substituted DB2 construct must record exactly one positional fragment");
  }

  /**
   * {@code expectedText} is matched against the fragment's tree text, which ANTLR renders with all
   * whitespace stripped — hence the run-together spellings.
   */
  private static void assertFragmentAt(
      int line, int charPos, String expectedText, String override) {
    PersistentData.Fragment fragment = PersistentData.fragmentAt(line, charPos);
    assertTrue(
        fragment != null && fragment.tree.getText().toUpperCase().contains(expectedText),
        "Line "
            + line
            + " must record the DB2 parse tree containing "
            + expectedText
            + ", which is what "
            + override
            + " is there to do; got "
            + (fragment == null ? "no fragment" : fragment.tree.getText()));
  }

  @Test
  void originalVisitorRecordsNoFragments() {
    PersistentData.reset();
    run(Db2SqlVisitorBuilder.ORIGINAL, freshContext(TEXT));

    assertEquals(
        0,
        PersistentData.fragmentCount(),
        "The original visitor must not record fragments — smojol's flag is what turns this on");
  }
}
