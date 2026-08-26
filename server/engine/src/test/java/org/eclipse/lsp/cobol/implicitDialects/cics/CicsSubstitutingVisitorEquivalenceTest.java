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
  private static final String TEXT =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. CICSTEST.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 WS-MSG PIC X(10).\n"
          + "        PROCEDURE DIVISION.\n"
          + "            EXEC CICS SEND TEXT FROM(WS-MSG) END-EXEC.\n";

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

    assertEquals(
        originalContext.getExtendedDocument().toString(),
        substitutingContext.getExtendedDocument().toString(),
        "Substitution must stay length-preserving and marker-free, so both documents must match");
  }

  @Test
  void substitutingVisitorRecordsOneFragmentForTheExecBlock() {
    PersistentData.reset();
    run(CICSVisitorBuilder.SUBSTITUTING, freshContext());

    assertEquals(
        1,
        PersistentData.fragmentCount(),
        "One EXEC CICS block must record exactly one positional fragment");
    PersistentData.Fragment fragment = PersistentData.fragmentAt(7, 12);
    assertTrue(
        fragment != null && fragment.tree.getText().toUpperCase().contains("SEND"),
        "The recorded fragment must be the EXEC CICS parse tree");
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
