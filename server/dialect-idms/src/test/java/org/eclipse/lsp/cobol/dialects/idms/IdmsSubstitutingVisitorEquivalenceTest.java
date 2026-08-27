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
package org.eclipse.lsp.cobol.dialects.idms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.lsp.cobol.common.AnalysisConfig;
import org.eclipse.lsp.cobol.common.copybook.CopybookProcessingMode;
import org.eclipse.lsp.cobol.common.dialects.CobolLanguageId;
import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.mapping.ExtendedDocument;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.poc.PersistentData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Closes the coverage gap that let the fork's copy of {@code IdmsVisitor} drift away from upstream's.
 * {@link IdmsDialect} has no builder seam and no config flag for IDMS — it always substitutes — so
 * nothing in the suite exercised the pristine visitor, and nothing compared the two.
 *
 * <p>Three kinds of assertion live here, and they do different jobs.
 *
 * <ul>
 *   <li><em>Equivalence</em> assertions compare {@link IdmsSubstitutingVisitor} against
 *       {@link IdmsVisitor}. They guard against the subclass drifting again.
 *   <li><em>Absolute</em> assertions pin exactly how the document must be blanked, as a golden
 *       document. Once the subclass is record-then-delegate, equivalence holds by construction for
 *       the delegating paths, so only these can catch a regression pushed into the shared parent.
 *   <li><em>Divergence</em> assertions pin the one place the subclass deliberately does <em>not</em>
 *       delegate — the IDMS {@code ON} path-status form — and assert both halves of the contrast:
 *       that upstream leaves no zero-width-space run there, and that the subclass does. That run is
 *       the only anchor smojol can graft onto, so a "simplification" of
 *       {@link IdmsSubstitutingVisitor#visitIdmsStatements} into a plain {@code super} call would
 *       silently lose every {@code ON}-clause fragment. See
 *       {@code ExtendedTextLine.clear(int, int)}: it writes a plain space, not
 *       {@code CobolDialect.FILLER}.
 * </ul>
 *
 * <p>{@link PersistentData} is static mutable state, so this class must not run in parallel with
 * anything else that analyses source.
 */
@Execution(ExecutionMode.SAME_THREAD)
class IdmsSubstitutingVisitorEquivalenceTest {

  private static final String URI = "file:///idms.cbl";

  /** {@code CobolDialect.FILLER}: the zero-width space every dialect blanks its source with. */
  private static final char FILLER = '​';

  /**
   * Reaches every override {@link IdmsSubstitutingVisitor} declares except the {@code ON}
   * path-status branch of {@code visitIdmsStatements} (that one needs {@link #ON_TEXT}), plus the two
   * upstream substitution paths the subclass must <em>not</em> record from. One construct per line:
   *
   * <ul>
   *   <li>lines 4-5, {@code IDMS-CONTROL SECTION.} + {@code PROTOCOL. ...} —
   *       {@code visitIdmsSections}, {@code idmsControlSection} alternative, spanning two lines
   *   <li>lines 7-8, {@code SCHEMA SECTION.} + {@code DB ... VERSION 1.} —
   *       {@code visitIdmsSections}, {@code schemaSection} alternative. Also the negative control for
   *       {@code visitSchemaSection}, which upstream deliberately leaves unsubstituted because it is
   *       a direct alternative of {@code idmsSections}: a second fragment here would share line 7's
   *       start position.
   *   <li>lines 9-10, {@code MAP SECTION.} + {@code MAP EMPMAP VERSION 1.} —
   *       {@code visitIdmsSections}, {@code mapSection} alternative
   *   <li>line 11-13 — plain COBOL. Negative controls: they must survive untouched, otherwise the
   *       golden-document test would pass on an all-blank document.
   *   <li>line 15, {@code BIND RUN-UNIT.} and line 16, {@code READY.} —
   *       {@code visitIdmsStatements} with no {@code imperativeStatementCall}, the delegating branch
   *   <li>line 17, {@code OBTAIN NEXT ... WHERE ...} — {@code obtainLRStatement}, a sibling of
   *       {@code idmsStatements} in {@code idmsRules}. Upstream's {@code visitObtainLRStatement}
   *       substitutes it, and the subclass must NOT record it: the fork never did, and a fragment
   *       here would be one smojol's graft step never claims. That the line is blanked at all is
   *       what proves the override was reached.
   *   <li>line 18, {@code ERASE ... FROM ...} — {@code eraseStatement} inside
   *       {@code idmsStmtsOptTermOn}, so {@code visitIdmsStatements} records it once while upstream's
   *       {@code visitEraseStoreModifyLrStatementsOptions} additionally blanks the nested
   *       {@code FROM} clause without recording. Exactly one fragment for this line.
   *   <li>line 19, {@code IF IX-EMP EMPTY} — {@code visitIdmsIfCondition}. The trailing
   *       {@code MOVE 'X' TO MT-FLAG.} is outside the recorded context and must survive.
   *   <li>line 20, {@code INQUIRE MAP ... IF ...} — {@code visitIdmsIfStatement}, which upstream
   *       prefixes with {@code _IF_ }, shifting the filler five columns right of the recorded start.
   *   <li>line 21, {@code FINISH.} — {@code visitIdmsStatements} whose stop token is
   *       {@code DOT_FS}, so the trailing dot must be restored rather than blanked.
   * </ul>
   */
  private static final String TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. IDMSSUB.\n"
          + "        ENVIRONMENT DIVISION.\n"
          + "        IDMS-CONTROL SECTION.\n"
          + "        PROTOCOL. MODE IS BATCH DEBUG.\n"
          + "        DATA DIVISION.\n"
          + "        SCHEMA SECTION.\n"
          + "        DB EMPSS01 WITHIN EMPSCHM VERSION 1.\n"
          + "        MAP SECTION.\n"
          + "        MAP EMPMAP VERSION 1.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 IX-EMP PIC X.\n"
          + "        01 MT-FLAG PIC X.\n"
          + "        PROCEDURE DIVISION.\n"
          + "            BIND RUN-UNIT.\n"
          + "            READY.\n"
          + "            OBTAIN NEXT EMPLR-REC WHERE FLD1 = 'A'\n"
          + "            ERASE EMPLR-REC FROM LR-AREA\n"
          + "            IF IX-EMP EMPTY MOVE 'X' TO MT-FLAG.\n"
          + "            INQUIRE MAP EMPMAP IF INPUT CHANGED.\n"
          + "            FINISH.\n";

  /**
   * The exact document {@link #TEXT} must become. Both visitors must produce this, because none of
   * {@link #TEXT}'s constructs takes the diverging {@code ON} path-status branch.
   *
   * <p>Reading the pattern: {@code addReplacementContext} blanks the whole context span in one go
   * with {@code replaceAll("[^ \n]", FILLER)}, so single spaces and newlines survive and everything
   * else becomes one FILLER per character; a trailing {@code DOT_FS} is put back afterwards.
   */
  private static final String EXPECTED_SUBSTITUTED_TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. IDMSSUB.\n"
          + "        ENVIRONMENT DIVISION.\n"
          // IDMS-CONTROL SECTION.
          + "        " + f(12) + " " + f(8) + "\n"
          // PROTOCOL. MODE IS BATCH DEBUG.  (stop token is DOT_FS, so the dot is restored)
          + "        " + f(9) + " " + f(4) + " " + f(2) + " " + f(5) + " " + f(5) + ".\n"
          + "        DATA DIVISION.\n"
          // SCHEMA SECTION.
          + "        " + f(6) + " " + f(8) + "\n"
          // DB EMPSS01 WITHIN EMPSCHM VERSION 1.
          + "        " + f(2) + " " + f(7) + " " + f(6) + " " + f(7) + " " + f(7) + " " + f(1)
          + ".\n"
          // MAP SECTION.
          + "        " + f(3) + " " + f(8) + "\n"
          // MAP EMPMAP VERSION 1.
          + "        " + f(3) + " " + f(6) + " " + f(7) + " " + f(1) + ".\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 IX-EMP PIC X.\n"
          + "        01 MT-FLAG PIC X.\n"
          + "        PROCEDURE DIVISION.\n"
          // BIND RUN-UNIT.
          + "            " + f(4) + " " + f(8) + ".\n"
          // READY.
          + "            " + f(5) + ".\n"
          // OBTAIN NEXT EMPLR-REC WHERE FLD1 = 'A'   (visitObtainLRStatement, no fragment)
          + "            " + f(6) + " " + f(4) + " " + f(9) + " " + f(5) + " " + f(4) + " " + f(1)
          + " " + f(3) + "\n"
          // ERASE EMPLR-REC FROM LR-AREA
          + "            " + f(5) + " " + f(9) + " " + f(4) + " " + f(7) + "\n"
          // IF IX-EMP EMPTY MOVE 'X' TO MT-FLAG.  (only the condition is inside the context)
          + "            IF " + f(6) + " " + f(5) + " MOVE 'X' TO MT-FLAG.\n"
          // INQUIRE MAP EMPMAP IF INPUT CHANGED.  (_IF_ prefix; the dot is outside the context)
          + "            _IF_ " + f(7) + " " + f(3) + " " + f(6) + " " + f(2) + " " + f(5) + " "
          + f(7) + ".\n"
          // FINISH.
          + "            " + f(6) + ".\n";

  /**
   * The {@code ON} path-status form, in both alternatives of the inlined {@code idmsStatements} rule,
   * so that {@code ctx.imperativeStatementCall()} is proven to resolve for each:
   *
   * <ul>
   *   <li>lines 8-9, {@code FINISH TASK} + {@code ON ANY-STATUS} — the
   *       {@code idmsStmtsOptTermOn endClause? imperativeStatementCall? idmsOnClause?} alternative,
   *       spanning two lines
   *   <li>line 12, {@code TRANSFER CONTROL TO PROG1 ON ANY-STATUS} — the
   *       {@code idmsStmtsMandTermOn (SEMICOLON_FS idmsOnClause? | DOT_FS | imperativeStatementCall)}
   *       alternative. Before this task an explicit {@code imperativeStatementCallOf} helper in the
   *       patched upstream visitor had to reach into two intermediate rule contexts to find this
   *       one; inlining those rules is what let the helper go.
   * </ul>
   *
   * <p>The trailing {@code MOVE}/{@code END-IF} lines are outside both contexts. They must survive,
   * because keeping them reachable under a COBOL {@code dialectIfStatment} is the whole reason the
   * {@code _IF_ } prefix exists.
   */
  private static final String ON_TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. IDMSON.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 WS-STATUS PIC X(4).\n"
          + "        01 WS-X PIC 9.\n"
          + "        PROCEDURE DIVISION.\n"
          + "            FINISH TASK\n"
          + "            ON ANY-STATUS\n"
          + "               MOVE 'DONE' TO WS-STATUS\n"
          + "            END-IF.\n"
          + "            TRANSFER CONTROL TO PROG1 ON ANY-STATUS\n"
          + "               MOVE 1 TO WS-X\n"
          + "            END-IF.\n";

  /** The exact document {@link #ON_TEXT} must become under {@link IdmsSubstitutingVisitor}. */
  private static final String EXPECTED_SUBSTITUTED_ON_TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. IDMSON.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 WS-STATUS PIC X(4).\n"
          + "        01 WS-X PIC 9.\n"
          + "        PROCEDURE DIVISION.\n"
          // FINISH TASK / ON ANY-STATUS, blanked with FILLER behind an _IF_ prefix
          + "            _IF_ " + f(6) + " " + f(4) + "\n"
          + "            " + f(2) + " " + f(10) + "\n"
          + "               MOVE 'DONE' TO WS-STATUS\n"
          + "            END-IF.\n"
          // TRANSFER CONTROL TO PROG1 ON ANY-STATUS
          + "            _IF_ " + f(8) + " " + f(7) + " " + f(2) + " " + f(5) + " " + f(2) + " "
          + f(10) + "\n"
          + "               MOVE 1 TO WS-X\n"
          + "            END-IF.\n";

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
   * Renders each filler run as {@code ~} so a mismatch reads as a diff of run lengths.
   */
  private static String render(String text) {
    return text.replace(FILLER, '~');
  }

  private MessageService messageService;

  @BeforeEach
  void setUp() {
    PersistentData.reset();
    messageService = mock(MessageService.class, withSettings().lenient());
    when(messageService.getMessage(anyString())).thenReturn("message");
    when(messageService.getMessage(anyString(), any(Object[].class))).thenReturn("message");
  }

  private static DialectProcessingContext freshContext(String text) {
    DialectProcessingContext context =
        DialectProcessingContext.builder()
            .extendedDocument(new ExtendedDocument(text, URI))
            .programDocumentUri(URI)
            .config(AnalysisConfig.defaultConfig(CopybookProcessingMode.ENABLED))
            .languageId(CobolLanguageId.COBOL.getId())
            .build();
    context.getExtendedDocument().commitTransformations();
    return context;
  }

  /**
   * Mirrors {@code IdmsDialect.processText}'s parse-then-visit, minus the copybook handling this test
   * does not need. {@link IdmsDialect} itself cannot be used for the "original" half of an
   * equivalence check, because IDMS has no visitor seam — it always substitutes.
   */
  private List<Node> run(IdmsVisitor visitor, DialectProcessingContext context) {
    IdmsLexer lexer =
        new IdmsLexer(CharStreams.fromString(context.getExtendedDocument().toString()));
    IdmsParser parser = new IdmsParser(new CommonTokenStream(lexer));
    DialectParserListener listener = new DialectParserListener(URI);
    lexer.removeErrorListeners();
    lexer.addErrorListener(listener);
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    parser.setErrorHandler(new CobolErrorStrategy(messageService));
    return new ArrayList<>(visitor.visitStartRule(parser.startRule()));
  }

  /** toString() reads baseText, which only picks up substitutions on commit. */
  private static String substitutedDocument(DialectProcessingContext context) {
    context.getExtendedDocument().commitTransformations();
    return context.getExtendedDocument().toString();
  }

  /**
   * {@code ExtendedDocument.toString()} joins its lines without a trailing separator, so the golden
   * constants — written with one {@code \n} per source line for readability — lose their last one.
   */
  private static String goldenDocument(String expected) {
    return expected.substring(0, expected.length() - 1);
  }

  private static List<String> nodeShapes(List<Node> nodes) {
    return nodes.stream()
        .flatMap(Node::getDepthFirstStream)
        .map(n -> n.getClass().getSimpleName() + "@" + n.getLocality().getRange())
        .sorted()
        .collect(Collectors.toList());
  }

  // ---------------------------------------------------------------- equivalence

  @Test
  void substitutingVisitorProducesTheSameNodesAsTheOriginal() {
    PersistentData.reset();
    DialectProcessingContext originalContext = freshContext(TEXT);
    List<Node> original = run(new IdmsVisitor(originalContext), originalContext);
    PersistentData.reset();
    DialectProcessingContext substitutingContext = freshContext(TEXT);
    List<Node> substituting =
        run(new IdmsSubstitutingVisitor(substitutingContext), substitutingContext);

    assertEquals(
        nodeShapes(original),
        nodeShapes(substituting),
        "Reparenting must not change which dialect nodes the IDMS visitor produces");
  }

  @Test
  void substitutingVisitorBlanksTheDocumentJustLikeTheOriginalWhereItDelegates() {
    PersistentData.reset();
    DialectProcessingContext originalContext = freshContext(TEXT);
    run(new IdmsVisitor(originalContext), originalContext);
    PersistentData.reset();
    DialectProcessingContext substitutingContext = freshContext(TEXT);
    run(new IdmsSubstitutingVisitor(substitutingContext), substitutingContext);

    assertEquals(
        render(substitutedDocument(originalContext)),
        render(substitutedDocument(substitutingContext)),
        "Away from the ON path-status branch the subclass only records and delegates, so both"
            + " documents must be identical — substitution stays length-preserving and marker-free"
            + " (each ~ is one FILLER character)");
  }

  @Test
  void substitutingVisitorReportsTheSameErrorsAsTheOriginal() {
    PersistentData.reset();
    DialectProcessingContext originalContext = freshContext(TEXT);
    IdmsVisitor original = new IdmsVisitor(originalContext);
    run(original, originalContext);
    PersistentData.reset();
    DialectProcessingContext substitutingContext = freshContext(TEXT);
    IdmsVisitor substituting = new IdmsSubstitutingVisitor(substitutingContext);
    run(substituting, substitutingContext);

    assertEquals(
        original.getErrors().stream().map(SyntaxError::toString).sorted().collect(Collectors.toList()),
        substituting.getErrors().stream()
            .map(SyntaxError::toString)
            .sorted()
            .collect(Collectors.toList()),
        "Reparenting must not change the errors the IDMS visitor collects");
  }

  // -------------------------------------------------------------------- absolute

  @Test
  void substitutedDocumentIsBlankedExactlyAsUpstreamBlanksIt() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(TEXT);
    run(new IdmsSubstitutingVisitor(context), context);

    assertEquals(
        render(goldenDocument(EXPECTED_SUBSTITUTED_TEXT)),
        render(substitutedDocument(context)),
        "Every substituted IDMS construct must be blanked exactly as upstream blanks it, with the"
            + " surrounding COBOL untouched (each ~ is one FILLER character)");
  }

  // ------------------------------------------------------------------ divergence

  /**
   * Upstream's half of the contrast. If this ever stops holding, {@code visitIdmsStatements} may be
   * collapsed into a plain {@code super} delegation and this whole class simplified.
   */
  @Test
  void upstreamLeavesNoFillerAnchorForAnOnPathStatusStatement() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(ON_TEXT);
    run(new IdmsVisitor(context), context);
    String document = substitutedDocument(context);

    assertEquals(
        -1,
        document.indexOf(FILLER),
        "Upstream substitutes an ON path-status statement via ExtendedDocument.clear, which writes"
            + " plain spaces, so it leaves no zero-width-space run anywhere — hence no"
            + " dialectNodeFiller for smojol to graft onto. Rendered: " + render(document));
    assertTrue(
        document.contains("IF 1 + 1 = 2"),
        "Upstream writes the literal text \"IF 1 + 1 = 2\" over the imperative statement call");
  }

  /** The subclass's half of the contrast. */
  @Test
  void onPathStatusStatementIsBlankedWithFillerBehindAnIfPrefix() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(ON_TEXT);
    run(new IdmsSubstitutingVisitor(context), context);
    String document = substitutedDocument(context);

    assertFalse(
        document.contains("IF 1 + 1 = 2"),
        "The subclass must not fall through to upstream's space-clearing path for an ON path-status"
            + " statement; rendered: " + render(document));
    assertEquals(
        render(goldenDocument(EXPECTED_SUBSTITUTED_ON_TEXT)),
        render(document),
        "Each ON path-status statement must be blanked with FILLER behind an _IF_ prefix, leaving"
            + " the trailing COBOL imperative statement intact (each ~ is one FILLER character)");
  }

  // --------------------------------------------------------------- fragments

  @Test
  void substitutingVisitorRecordsOneFragmentPerSubstitutedConstruct() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(TEXT);
    run(new IdmsSubstitutingVisitor(context), context);

    // One anchor per override, claimed in document order and checked before the count, so that a
    // deleted override fails with the line and the method name rather than an off-by-one total.
    // Coordinates are ANTLR's: 1-based line, 0-based column.
    claimFragmentAt(4, 8, "IDMS-CONTROL", "visitIdmsSections (idmsControlSection)");
    claimFragmentAt(7, 8, "SCHEMASECTION", "visitIdmsSections (schemaSection)");
    claimFragmentAt(9, 8, "MAPSECTION", "visitIdmsSections (mapSection)");
    claimFragmentAt(15, 12, "BINDRUN-UNIT", "visitIdmsStatements (no imperativeStatementCall)");
    claimFragmentAt(16, 12, "READY", "visitIdmsStatements (no imperativeStatementCall)");
    claimFragmentAt(18, 12, "ERASEEMPLR-REC", "visitIdmsStatements (nested ERASE ... FROM clause)");
    claimFragmentAt(19, 15, "IX-EMPEMPTY", "visitIdmsIfCondition");
    claimFragmentAt(20, 12, "INQUIREMAP", "visitIdmsIfStatement");
    claimFragmentAt(21, 12, "FINISH", "visitIdmsStatements (stop token is DOT_FS)");

    // Exact, not a lower bound: this is what catches a *spurious* extra fragment, which no per-line
    // anchor can see. A wider override set is a defect too — it creates fragments smojol's graft
    // step never claims.
    assertEquals(
        9,
        PersistentData.fragmentCount(),
        "Each substituted IDMS construct must record exactly one positional fragment");
  }

  @Test
  void obtainLogicalRecordStatementIsSubstitutedButNotRecorded() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(TEXT);
    run(new IdmsSubstitutingVisitor(context), context);

    // Line 17 is blanked (see the golden document), which proves visitObtainLRStatement ran; it must
    // still contribute no fragment, exactly as the fork's copy did.
    assertNull(
        PersistentData.fragmentAt(17, 12),
        "obtainLRStatement is substituted through upstream's space-clearing helper and so has no"
            + " filler anchor; recording it would create a fragment the graft step never claims");
  }

  @Test
  void schemaSectionIsRecordedOnceNotTwice() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(TEXT);
    run(new IdmsSubstitutingVisitor(context), context);

    PersistentData.Fragment first = PersistentData.claim(7, 8);
    assertNotNull(first, "Precondition: the SCHEMA SECTION must record a fragment at 7:8");
    assertNull(
        PersistentData.claim(7, 8),
        "visitSchemaSection must not record: schemaSection is a direct alternative of idmsSections,"
            + " so a second fragment would share line 7's start position and one of the two would"
            + " never be claimed");
  }

  @Test
  void onPathStatusStatementsRecordFragmentsForBothGrammarAlternatives() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(ON_TEXT);
    run(new IdmsSubstitutingVisitor(context), context);

    claimFragmentAt(8, 12, "FINISHTASKONANY-STATUS", "idmsStmtsOptTermOn alternative");
    claimFragmentAt(12, 12, "TRANSFERCONTROLTOPROG1ONANY-STATUS", "idmsStmtsMandTermOn alternative");

    assertEquals(
        2,
        PersistentData.fragmentCount(),
        "Both alternatives of idmsStatements must expose imperativeStatementCall and record exactly"
            + " one fragment each");
  }

  /**
   * The {@code _IF_ } prefix is the one substitution that is not length-preserving, so the filler run
   * ends up five columns right of the recorded start. {@code Fragment.covers} is a range test for
   * exactly this reason; without it the shifted filler would find nothing to claim.
   */
  @Test
  void theIfPrefixedFragmentIsStillFoundAtTheShiftedFillerColumn() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(ON_TEXT);
    run(new IdmsSubstitutingVisitor(context), context);

    PersistentData.Fragment atStart = PersistentData.fragmentAt(8, 12);
    PersistentData.Fragment atFiller = PersistentData.fragmentAt(8, 17);
    assertNotNull(atStart, "Precondition: a fragment must be recorded at the context start 8:12");
    assertSame(
        atStart,
        atFiller,
        "The 5-column _IF_ shift must not hide the fragment from a lookup at the filler's own"
            + " column");
  }

  @Test
  void originalVisitorRecordsNoFragments() {
    PersistentData.reset();
    DialectProcessingContext context = freshContext(TEXT);
    run(new IdmsVisitor(context), context);
    assertEquals(
        0,
        PersistentData.fragmentCount(),
        "Pristine IdmsVisitor must not record fragments — the subclass is what turns this on");

    PersistentData.reset();
    DialectProcessingContext onContext = freshContext(ON_TEXT);
    run(new IdmsVisitor(onContext), onContext);
    assertEquals(
        0,
        PersistentData.fragmentCount(),
        "Pristine IdmsVisitor must not record fragments on the ON path-status form either");
  }

  // ---------------------------------------------------------------- helpers

  /**
   * Consuming per-construct anchor check. Claiming rather than peeking means a duplicate recorded at
   * the same start position cannot hide behind the fragment in front of it.
   *
   * <p>{@code expectedText} is matched against the fragment's tree text, which ANTLR renders with all
   * whitespace stripped — hence the run-together spellings.
   */
  private static void claimFragmentAt(
      int line, int charPos, String expectedText, String override) {
    PersistentData.Fragment fragment = PersistentData.claim(line, charPos);
    assertTrue(
        fragment != null && fragment.tree.getText().toUpperCase().contains(expectedText),
        "Line "
            + line
            + " must record the IDMS parse tree containing "
            + expectedText
            + ", which is what "
            + override
            + " is there to do; got "
            + (fragment == null ? "no fragment" : fragment.tree.getText()));
  }
}
