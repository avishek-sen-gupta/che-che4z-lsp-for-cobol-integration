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
package org.eclipse.lsp.cobol.implicitDialects.cics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.lsp.cobol.common.copybook.CopybookService;
import org.eclipse.lsp.cobol.common.dialects.CobolLanguageId;
import org.eclipse.lsp.cobol.common.dialects.DialectOutcome;
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
 * Closes the coverage gap that let {@code CicsSubstitutingVisitor} drift away from
 * {@link CICSVisitor}. {@code DialectService} selects the substituting visitor only when
 * {@code AnalysisConfig.isAddCicsPlaceholder()} is set, so the whole engine suite exercises the
 * original visitor and nothing exercised the fork's copy.
 *
 * <p>The contract asserted here is: the substituting visitor produces the same nodes and the same
 * errors as the original, and additionally records one positional fragment per substituted EXEC
 * CICS block.
 */
@Execution(ExecutionMode.SAME_THREAD)
class CicsSubstitutingVisitorEquivalenceTest {

  private static final String URI = "file:///cics.cbl";

  /**
   * Every EXEC CICS block below is here to span one of the four upstream behaviours the fork's
   * copy had silently lost, so that re-introducing the copy fails a test instead of only being
   * described in a commit message:
   *
   * <ul>
   *   <li>{@code SEND TEXT} — a plain, well-formed block; the baseline.
   *   <li>{@code ABEND ... CANCEL} — {@code cics_abend} handling. The copy produced
   *       {@code ExecCicsNode} here where upstream produces {@code ExecCicsAbendNode}.
   *   <li>{@code READ FILE(...)} with neither INTO nor SET and no RIDFLD — mandatory/exclusive
   *       option validation in {@code CICSOptionsCheckUtility}, reached from
   *       {@code CICSVisitor.visitChildren}. The copy overrode {@code visitChildren} without it
   *       and dropped both diagnostics.
   *   <li>a trailing block with no {@code END-EXEC} — the {@code cicsParser.missingEndExec}
   *       diagnostic, which the copy never emitted.
   * </ul>
   */
  private static final String TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. CICSTEST.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 WS-MSG PIC X(10).\n"
          + "        PROCEDURE DIVISION.\n"
          + "            EXEC CICS SEND TEXT FROM(WS-MSG) END-EXEC.\n"
          + "            EXEC CICS ABEND ABCODE('1234') CANCEL END-EXEC.\n"
          + "            EXEC CICS READ FILE('FILE1') END-EXEC.\n"
          + "            EXEC CICS LINK PROGRAM('PGM1')\n";

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

  private static DialectProcessingContext freshContext() {
    DialectProcessingContext context =
        DialectProcessingContext.builder()
            .extendedDocument(new ExtendedDocument(TEXT, URI))
            .programDocumentUri(URI)
            .languageId(CobolLanguageId.COBOL.getId())
            .build();
    context.getExtendedDocument().commitTransformations();
    return context;
  }

  private DialectOutcome run(CICSVisitorBuilder builder, DialectProcessingContext context) {
    return new CICSDialect(copybookService, messageService, builder)
        .processText(context)
        .getResult();
  }

  private List<SyntaxError> errorsOf(CICSVisitorBuilder builder, DialectProcessingContext context) {
    return new CICSDialect(copybookService, messageService, builder)
        .processText(context)
        .getErrors();
  }

  private static List<String> nodeShapes(List<Node> nodes) {
    return nodes.stream()
        .map(n -> n.getClass().getSimpleName() + "@" + n.getLocality().getRange())
        .sorted()
        .collect(Collectors.toList());
  }

  @Test
  void substitutingVisitorProducesTheSameNodesAsTheOriginal() {
    PersistentData.reset();
    List<Node> original = run(CICSVisitorBuilder.ORIGINAL, freshContext()).getDialectNodes();
    PersistentData.reset();
    List<Node> substituting =
        run(CICSVisitorBuilder.SUBSTITUTING, freshContext()).getDialectNodes();

    assertEquals(
        nodeShapes(original),
        nodeShapes(substituting),
        "Reparenting must not change which dialect nodes the CICS visitor produces");
  }

  @Test
  void substitutingVisitorProducesTheSameErrorsAsTheOriginal() {
    PersistentData.reset();
    List<String> original =
        errorsOf(CICSVisitorBuilder.ORIGINAL, freshContext()).stream()
            .map(SyntaxError::toString)
            .sorted()
            .collect(Collectors.toList());
    PersistentData.reset();
    List<String> substituting =
        errorsOf(CICSVisitorBuilder.SUBSTITUTING, freshContext()).stream()
            .map(SyntaxError::toString)
            .sorted()
            .collect(Collectors.toList());

    assertEquals(
        original,
        substituting,
        "Reparenting must not change which errors the CICS visitor reports");
  }

  @Test
  void substitutingVisitorBlanksTheExecBlockJustLikeTheOriginal() {
    PersistentData.reset();
    DialectProcessingContext originalContext = freshContext();
    run(CICSVisitorBuilder.ORIGINAL, originalContext);
    PersistentData.reset();
    DialectProcessingContext substitutingContext = freshContext();
    run(CICSVisitorBuilder.SUBSTITUTING, substitutingContext);

    // toString() reads baseText, which only picks up substitutions on commit. Without these two
    // calls the assertion would compare "blanked in place" against "blanked at all" rather than
    // comparing the resulting documents.
    originalContext.getExtendedDocument().commitTransformations();
    substitutingContext.getExtendedDocument().commitTransformations();

    assertEquals(
        originalContext.getExtendedDocument().toString(),
        substitutingContext.getExtendedDocument().toString(),
        "Substitution must stay length-preserving and marker-free, so both documents must match");
  }

  @Test
  void substitutingVisitorRecordsOneFragmentPerExecBlock() {
    PersistentData.reset();
    run(CICSVisitorBuilder.SUBSTITUTING, freshContext());

    // One anchor per block, checked before the count so that a lost anchor fails with the line and
    // the construct rather than with an off-by-one total. Every block must be reachable by document
    // position, which is the whole premise of positional correlation.
    // Coordinates are ANTLR's: 1-based line, 0-based column.
    assertFragmentAt(7, "SEND");
    assertFragmentAt(8, "ABEND");
    assertFragmentAt(9, "READ");
    assertFragmentAt(10, "LINK");

    // Exact, not a lower bound: this is what catches a *spurious* extra fragment, which no
    // per-line anchor can see.
    assertEquals(
        4,
        PersistentData.fragmentCount(),
        "Each of the four EXEC CICS blocks must record exactly one positional fragment");
  }

  private static void assertFragmentAt(int line, String expectedToken) {
    PersistentData.Fragment fragment = PersistentData.fragmentAt(line, 12);
    assertTrue(
        fragment != null && fragment.tree.getText().toUpperCase().contains(expectedToken),
        "The fragment recorded at line "
            + line
            + " must be the EXEC CICS parse tree containing "
            + expectedToken);
  }

  @Test
  void originalVisitorRecordsNoFragments() {
    PersistentData.reset();
    run(CICSVisitorBuilder.ORIGINAL, freshContext());

    assertEquals(
        0,
        PersistentData.fragmentCount(),
        "The original visitor must not record fragments — smojol's flag is what turns this on");
  }
}
