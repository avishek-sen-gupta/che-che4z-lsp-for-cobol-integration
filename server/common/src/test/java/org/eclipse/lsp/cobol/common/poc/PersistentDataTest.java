package org.eclipse.lsp.cobol.common.poc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Unit tests for the positional fragment registry. These manipulate static state, so the class is
 * pinned to a single thread — this module's surefire config sets {@code <parallel>all</parallel>}.
 */
@Execution(ExecutionMode.SAME_THREAD)
class PersistentDataTest {

  @BeforeEach
  void reset() {
    PersistentData.reset();
  }

  /** Builds a context whose start/stop tokens sit at the given coordinates. */
  private static ParserRuleContext contextAt(
      int startLine, int startChar, int endLine, int endChar) {
    ParserRuleContext ctx = new ParserRuleContext();
    CommonToken start = new CommonToken(1, "start");
    start.setLine(startLine);
    start.setCharPositionInLine(startChar);
    CommonToken stop = new CommonToken(1, "stop");
    stop.setLine(endLine);
    stop.setCharPositionInLine(endChar);
    ctx.start = start;
    ctx.stop = stop;
    return ctx;
  }

  @Test
  void recordStoresFragmentCoordinatesAndTree() {
    ParserRuleContext ctx = contextAt(12, 11, 13, 20);
    PersistentData.record(ctx, LocalisedDialect.IDMS);

    assertEquals(1, PersistentData.fragmentCount());
    PersistentData.Fragment fragment = PersistentData.fragmentAt(12, 11);
    assertEquals(12, fragment.startLine);
    assertEquals(11, fragment.startChar);
    assertEquals(13, fragment.endLine);
    assertEquals(LocalisedDialect.IDMS, fragment.dialect);
    assertSame(ctx, fragment.tree);
  }

  @Test
  void coversAcceptsPositionsAtOrAfterStartOnStartLine() {
    PersistentData.record(contextAt(12, 11, 13, 20), LocalisedDialect.IDMS);

    assertTrue(PersistentData.isCovered(12, 11), "start position must be covered");
    assertTrue(
        PersistentData.isCovered(12, 16),
        "a position 5 columns right of start must be covered — this is the IDMS _IF_ prefix case");
    assertFalse(PersistentData.isCovered(12, 10), "a position left of start must not be covered");
  }

  @Test
  void coversSpansToEndLineAndRejectsBeyond() {
    PersistentData.record(contextAt(12, 11, 13, 20), LocalisedDialect.IDMS);

    assertTrue(PersistentData.isCovered(13, 0), "any column on a later line must be covered");
    assertFalse(PersistentData.isCovered(14, 0), "a line past endLine must not be covered");
    assertFalse(PersistentData.isCovered(11, 99), "a line before startLine must not be covered");
  }

  @Test
  void claimConsumesFragmentSoItIsGraftedAtMostOnce() {
    PersistentData.record(contextAt(5, 0, 5, 10), LocalisedDialect.CICS);

    assertNotNull(PersistentData.claim(5, 0), "first claim must succeed");
    assertNull(PersistentData.claim(5, 0), "a claimed fragment must not be returned twice");
  }

  @Test
  void claimReturnsTheEarliestUnclaimedCoveringFragment() {
    ParserRuleContext first = contextAt(5, 0, 5, 10);
    ParserRuleContext second = contextAt(5, 0, 5, 10);
    PersistentData.record(first, LocalisedDialect.CICS);
    PersistentData.record(second, LocalisedDialect.CICS);

    assertSame(first, PersistentData.claim(5, 0).tree);
    assertSame(second, PersistentData.claim(5, 0).tree);
    assertNull(PersistentData.claim(5, 0));
  }

  @Test
  void isCoveredIgnoresWhetherFragmentWasClaimed() {
    PersistentData.record(contextAt(5, 0, 5, 10), LocalisedDialect.DB2_SQL);
    PersistentData.claim(5, 0);

    assertTrue(
        PersistentData.isCovered(5, 0),
        "isCovered must stay true after claim — it answers 'was this region a dialect fragment?'");
  }

  @Test
  void resetClearsFragmentsAndClaims() {
    PersistentData.record(contextAt(5, 0, 5, 10), LocalisedDialect.IDMS);
    PersistentData.claim(5, 0);

    PersistentData.reset();

    assertEquals(0, PersistentData.fragmentCount());
    PersistentData.record(contextAt(5, 0, 5, 10), LocalisedDialect.IDMS);
    assertEquals(
        1,
        PersistentData.fragmentCount(),
        "after reset a positionally identical fragment must be recordable and claimable again");
    assertNotNull(PersistentData.claim(5, 0), "claimed set must have been cleared by reset");
  }

  @Test
  void recordToleratesMissingStopToken() {
    ParserRuleContext ctx = contextAt(7, 4, 7, 9);
    ctx.stop = null;

    PersistentData.record(ctx, LocalisedDialect.IDMS);

    PersistentData.Fragment fragment = PersistentData.fragmentAt(7, 4);
    assertEquals(
        7, fragment.endLine, "with no stop token endLine must fall back to the start token's line");
  }

  @Test
  void lookupOnEmptyRegistryReturnsNullNotAnException() {
    assertNull(PersistentData.fragmentAt(1, 0));
    assertNull(PersistentData.claim(1, 0));
    assertFalse(PersistentData.isCovered(1, 0));
  }

  @Test
  void persistentDataExposesOnlyThePositionalApi() {
    Set<String> methodNames = new TreeSet<>();
    for (Method m : PersistentData.class.getDeclaredMethods()) {
      if (m.isSynthetic()) continue;
      methodNames.add(m.getName());
    }

    assertEquals(
        new TreeSet<>(
            Arrays.asList(
                "claim", "fragmentAt", "fragmentCount", "isCovered", "record", "reset")),
        methodNames,
        "The guid-keyed API (next/addDialectTree/getDialectNode/dialect/treeCount) must be gone");

    Set<String> fieldNames = new TreeSet<>();
    for (Field f : PersistentData.class.getDeclaredFields()) {
      if (f.isSynthetic()) continue;
      fieldNames.add(f.getName());
    }

    assertEquals(
        new TreeSet<>(Arrays.asList("claimed", "fragments")),
        fieldNames,
        "Only the fragments list and claimed set may remain as static state");
  }
}
