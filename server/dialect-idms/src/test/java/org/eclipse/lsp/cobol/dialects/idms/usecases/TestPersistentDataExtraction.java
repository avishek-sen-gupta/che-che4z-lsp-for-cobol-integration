/*
 * Copyright (c) 2022 Broadcom.
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
package org.eclipse.lsp.cobol.dialects.idms.usecases;

import com.google.common.collect.ImmutableList;
import org.eclipse.lsp.cobol.common.AnalysisResult;
import org.eclipse.lsp.cobol.common.poc.LocalisedDialect;
import org.eclipse.lsp.cobol.common.poc.PersistentData;
import org.eclipse.lsp.cobol.common.poc.PersistentData.Fragment;
import org.eclipse.lsp.cobol.dialects.idms.IdmsDialect;
import org.eclipse.lsp.cobol.dialects.idms.utils.Fixtures;
import org.eclipse.lsp.cobol.test.engine.UseCase;
import org.eclipse.lsp.cobol.test.engine.UseCaseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the smojol-fork extraction mechanism: after IDMS dialect preprocessing, each DML
 * statement has been blanked out of the extended document length-preservingly, and the region it
 * occupied — start line, start column, end line — is recorded in {@link PersistentData} together
 * with the original IDMS parse tree and the dialect that produced it.
 *
 * <p>Correlation is purely positional: because the blanking preserves length, the recorded start
 * position is still the position of the filler run the COBOL parser later produces in the
 * fragment's place. No marker text is injected into the document.
 *
 * <p>These tests verify the <em>extraction</em> half of the pipeline (che4z side). The
 * <em>reinjection</em> half ({@code DialectIntegratorListener}) is tested separately in {@code
 * IdmsDialectIntegrationTest} in the smojol-toolkit module.
 *
 * <p>{@link PersistentData} is static mutable state, so this class must not run in parallel with
 * anything else that analyses source.
 */
@Execution(ExecutionMode.SAME_THREAD)
class TestPersistentDataExtraction {

  private static final String BOILERPLATE =
      "        IDENTIFICATION DIVISION.\n"
          + "        PROGRAM-ID. EXTRACTTEST.\n"
          + "        DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        PROCEDURE DIVISION.\n";

  @BeforeEach
  void resetPersistentData() {
    PersistentData.reset();
  }

  // ---------- single-statement extraction ----------

  @Test
  void singleFinishStatementProducesOneExtraction() {
    String source = BOILERPLATE + "            FINISH.\n";
    analyze(source);

    assertEquals(1, PersistentData.fragmentCount(),
        "Expected exactly one recorded fragment for a single FINISH statement");
  }

  @Test
  void recordedFragmentCarriesIdmsDialectAndAParseTree() {
    String source = BOILERPLATE + "            FINISH.\n";
    analyze(source);

    // FINISH sits on line 7 (BOILERPLATE + subschema copy header + statement), indented 12 columns.
    Fragment fragment = PersistentData.fragmentAt(7, 12);
    assertNotNull(fragment, "A fragment must cover the FINISH statement at 7:12");
    assertEquals(LocalisedDialect.IDMS, fragment.dialect);
    assertNotNull(fragment.tree, "The recorded fragment must carry the IDMS parse tree");
    assertTrue(fragment.tree.getText().toUpperCase().contains("FINISH"),
        "The recorded tree must be the IDMS statement, got: " + fragment.tree.getText());
  }

  // ---------- multi-statement extraction ----------

  @Test
  void threeIdmsStatementsProduceThreeExtractions() {
    String source =
        BOILERPLATE
            + "            BIND RUN-UNIT.\n"
            + "            READY.\n"
            + "            FINISH.\n";
    analyze(source);

    assertEquals(3, PersistentData.fragmentCount(),
        "Expected three recorded fragments for BIND + READY + FINISH");
  }

  // ---------- interleaved COBOL + IDMS ----------

  @Test
  void onlyIdmsStatementsAreExtractedNotCobol() {
    // Source has 2 MOVE statements (pure COBOL) and 2 IDMS statements
    String source =
        BOILERPLATE
            + "            MOVE 0 TO WS-X\n"
            + "            FIND CALC EMPLOYEE-RECORD\n"
            + "            MOVE 1 TO WS-X\n"
            + "            FINISH.\n";
    analyze(source);

    // Only FIND and FINISH should have been extracted
    assertEquals(2, PersistentData.fragmentCount(),
        "Expected exactly 2 recorded fragments (FIND + FINISH); "
            + "MOVE statements must not be extracted");
  }

  @Test
  void resetClearsFragmentsBetweenAnalysisCalls() {
    String source = BOILERPLATE + "            FINISH.\n";

    analyze(source);
    int afterFirst = PersistentData.fragmentCount();
    assertTrue(afterFirst > 0, "After first analysis, at least one fragment must be recorded");

    PersistentData.reset();
    assertEquals(0, PersistentData.fragmentCount(), "After reset(), no fragments must remain");

    analyze(source);
    assertEquals(
        afterFirst,
        PersistentData.fragmentCount(),
        "A fresh analysis after reset() must record the same fragment count as the first");
  }

  // ---------- IF statement extraction (Issue 2 investigation) ----------

  /**
   * Grammar: {@code ifStatement : IF idmsIfCondition}
   * {@code idmsIfCondition : (idms_db_entity_name idmsIfEmpty) | (idmsIfMember)}
   *
   * <p>The IDMS visitor overrides {@code visitIdmsIfCondition}, which calls
   * {@code replaceWithMetadata} on the condition node. There is no {@code visitIfStatement}
   * override, so the outer {@code ifStatement} wrapper is NOT extracted — only the condition
   * node produces one entry. This test verifies that an {@code IF <entity> EMPTY} statement
   * produces exactly one extraction, confirming that double-extraction does NOT occur for
   * this grammar path.
   */
  @Test
  void idmsIfEmptyConditionProducesExactlyOneExtraction() {
    // WORKING-STORAGE variables needed so the COBOL semantic analyzer recognises
    // the identifiers; the fragments are recorded during IDMS preprocessing
    // (before COBOL analysis), so they are not affected by semantic errors.
    String source =
        "        IDENTIFICATION DIVISION.\n"
            + "        PROGRAM-ID. IFTEST.\n"
            + "        DATA DIVISION.\n"
            + "        WORKING-STORAGE SECTION.\n"
            + "        01 IX-EMP PIC X.\n"
            + "        01 MT-FLAG PIC X.\n"
            + "        PROCEDURE DIVISION.\n"
            + "            IF IX-EMP EMPTY MOVE 'X' TO MT-FLAG.\n";
    analyze(source);

    assertEquals(1, PersistentData.fragmentCount(),
        "IF <entity> EMPTY must produce exactly one extraction "
            + "(visitIdmsIfCondition called once; no double-extraction)");
  }

  /**
   * Mirror of {@link #idmsIfEmptyConditionProducesExactlyOneExtraction} for the MEMBER form:
   * {@code idmsIfMember : NOT? idms_db_entity_name MEMBER}.
   */
  @Test
  void idmsIfMemberConditionProducesExactlyOneExtraction() {
    String source =
        "        IDENTIFICATION DIVISION.\n"
            + "        PROGRAM-ID. IFTEST.\n"
            + "        DATA DIVISION.\n"
            + "        WORKING-STORAGE SECTION.\n"
            + "        01 IX-EMP PIC X.\n"
            + "        01 MT-FLAG PIC X.\n"
            + "        PROCEDURE DIVISION.\n"
            + "            IF NOT IX-EMP MEMBER MOVE 'X' TO MT-FLAG.\n";
    analyze(source);

    assertEquals(1, PersistentData.fragmentCount(),
        "IF NOT <entity> MEMBER must produce exactly one extraction "
            + "(visitIdmsIfCondition called once; no double-extraction)");
  }

  /**
   * Grammar: {@code idmsIfStatement : inquireMapIfStatement}
   * {@code inquireMapIfStatement : INQUIRE MAP idms_map_name IF inqMapIfPhrase}
   *
   * <p>The visitor overrides {@code visitIdmsIfStatement}, which calls {@code replaceWithMetadata}
   * on the whole {@code inquireMapIfStatement} context. {@code inqMapIfPhrase} is NOT
   * {@code idmsIfCondition}, so {@code visitIdmsIfCondition} is NOT triggered. Exactly one
   * extraction must occur.
   */
  @Test
  void inquireMapIfProducesExactlyOneExtraction() {
    String source =
        BOILERPLATE
            + "            INQUIRE MAP EMPMAP IF INPUT CHANGED.\n";
    analyze(source);

    assertEquals(1, PersistentData.fragmentCount(),
        "INQUIRE MAP <name> IF INPUT CHANGED must produce exactly one extraction "
            + "(visitIdmsIfStatement called once; visitIdmsIfCondition must NOT fire for inqMapIfPhrase)");
  }

  /**
   * Both a plain {@code IF <condition>} (handled by {@code visitIdmsIfCondition}) and an
   * {@code INQUIRE MAP <name> IF ...} (handled by {@code visitIdmsIfStatement}) are present.
   * Because they are siblings in the {@code idmsRules} grammar alternative, neither triggers
   * the other. The combined extraction count must be exactly 2.
   */
  @Test
  void idmsIfConditionAndInquireMapIfTogetherProduceTwoExtractions() {
    String source =
        "        IDENTIFICATION DIVISION.\n"
            + "        PROGRAM-ID. IFTEST.\n"
            + "        DATA DIVISION.\n"
            + "        WORKING-STORAGE SECTION.\n"
            + "        01 IX-EMP PIC X.\n"
            + "        01 MT-FLAG PIC X.\n"
            + "        PROCEDURE DIVISION.\n"
            + "            IF IX-EMP EMPTY MOVE 'X' TO MT-FLAG.\n"
            + "            INQUIRE MAP EMPMAP IF INPUT CHANGED.\n";
    analyze(source);

    assertEquals(2, PersistentData.fragmentCount(),
        "One IF <condition> + one INQUIRE MAP IF must produce exactly 2 extractions; "
            + "if the count differs from 2, double-extraction or missed extraction has occurred");
  }

  // ---------- helper ----------

  private static AnalysisResult analyze(String source) {
    UseCase useCase =
        UseCase.builder()
            .text(source)
            .copybook(Fixtures.subschemaCopy(""))
            .dialects(ImmutableList.of(IdmsDialect.NAME))
            .build();
    return UseCaseUtils.analyze(useCase);
  }
}
