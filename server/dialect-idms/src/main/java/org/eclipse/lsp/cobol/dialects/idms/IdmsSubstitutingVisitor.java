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
 *    Broadcom - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.dialects.idms;

import static org.eclipse.lsp.cobol.dialects.idms.IdmsParser.DOT_FS;

import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp.cobol.common.dialects.CobolDialect;
import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.poc.LocalisedDialect;
import org.eclipse.lsp.cobol.common.poc.PersistentData;
import org.eclipse.lsp.cobol.dialects.idms.IdmsParser.IdmsIfConditionContext;
import org.eclipse.lsp.cobol.dialects.idms.IdmsParser.IdmsIfStatementContext;
import org.eclipse.lsp.cobol.dialects.idms.IdmsParser.IdmsSectionsContext;
import org.eclipse.lsp.cobol.dialects.idms.IdmsParser.IdmsStatementsContext;

/**
 * An {@link IdmsVisitor} that additionally records the extended-document region each substituted
 * IDMS fragment occupied, together with its parse tree, so smojol can graft the IDMS subtree back
 * onto the COBOL parse tree afterwards.
 *
 * <p>Correlation between a recorded fragment and the {@code dialectNodeFiller} the COBOL parser
 * later produces in its place is purely positional, which is why the substitution has to stay
 * length-preserving and marker-free.
 *
 * <p>Four overrides record a fragment; three of those simply delegate to {@code super} afterwards.
 * Two cannot delegate on one branch each, both for the same underlying reason: when an IDMS statement
 * carries a trailing COBOL imperative statement (the {@code ON} path-status form) upstream calls
 * {@code addReplacementImperativeStatementContext}, which uses {@code ExtendedDocument.clear(range)}
 * plus the literal text {@code "IF 1 + 1 = 2"}. {@code clear} bottoms out in
 * {@code ExtendedTextLine.clear(int, int)}, which writes a plain space rather than
 * {@link CobolDialect#FILLER}, so that path leaves no filler run for the COBOL parser to turn into a
 * {@code dialectNodeFiller} — and therefore no anchor to graft onto. Those two branches blank with
 * FILLER instead, prefixing {@code _IF_ } so the COBOL parser still matches a
 * {@code dialectIfStatment} and the trailing imperative statement stays reachable. They are
 * {@link #visitIdmsStatements}, where the {@code ON} clause is the statement's own, and
 * {@link #visitEraseStoreModifyLrStatementsOptions}, where it belongs to a nested clause and the
 * enclosing statement has already been blanked by the time upstream would clear it.
 *
 * <p>{@code visitObtainLRStatement} is deliberately <em>not</em> overridden, even though it
 * substitutes: it was never recorded by the fork, and recording it would create a fragment the graft
 * step never claims.
 *
 * <p>{@code visitSchemaSection} is deliberately not overridden either: {@code schemaSection} is a
 * direct alternative of {@code idmsSections}, which {@link #visitIdmsSections} already records, so
 * recording it again would create two fragments sharing a start position.
 */
class IdmsSubstitutingVisitor extends IdmsVisitor {
  private static final String IF = "_IF_ ";

  private final DialectProcessingContext context;

  IdmsSubstitutingVisitor(DialectProcessingContext context) {
    super(context);
    this.context = context;
  }

  @Override
  public List<Node> visitIdmsStatements(IdmsStatementsContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.IDMS);
    if (ctx.imperativeStatementCall() == null) {
      return super.visitIdmsStatements(ctx);
    }
    // Do not delegate: upstream would write "IF 1 + 1 = 2" over a space-cleared region, leaving no
    // filler anchor for the graft step. Blank with FILLER and keep an _IF_ prefix instead.
    blankWithPrefix(ctx, IF);
    return visitChildren(ctx);
  }

  @Override
  public List<Node> visitIdmsSections(IdmsSectionsContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.IDMS);
    return super.visitIdmsSections(ctx);
  }

  @Override
  public List<Node> visitIdmsIfStatement(IdmsIfStatementContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.IDMS);
    return super.visitIdmsIfStatement(ctx);
  }

  @Override
  public List<Node> visitIdmsIfCondition(IdmsIfConditionContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.IDMS);
    return super.visitIdmsIfCondition(ctx);
  }

  /**
   * Records nothing — {@link #visitIdmsStatements} has already recorded the enclosing statement,
   * {@code ON} clause included — but must still intervene on the {@code ON} path-status branch, for
   * the same reason {@link #visitIdmsStatements} does.
   *
   * <p>This runs <em>after</em> the enclosing {@code idmsStatements} has been blanked, and upstream
   * re-substitutes {@code ctx.getParent()} — the whole {@code eraseStatement}/{@code storeStatement}/
   * {@code modifyStatement}, which shares its start token with the statement already blanked. So
   * delegating here does not merely fail to add an anchor, it destroys the one already written: the
   * space-clear overwrites every filler character the outer blanking produced, leaving a document
   * with no {@code ZERO_WIDTH_SPACE} at all and a recorded fragment that the graft step can never
   * claim. Blank the parent with FILLER behind the same {@code _IF_ } prefix instead, which keeps the
   * anchor at the recorded fragment's own start position.
   */
  @Override
  public List<Node> visitEraseStoreModifyLrStatementsOptions(
      IdmsParser.EraseStoreModifyLrStatementsOptionsContext ctx) {
    if (ctx.imperativeStatementCall() == null) {
      return super.visitEraseStoreModifyLrStatementsOptions(ctx);
    }
    blankWithPrefix(ctx.getParent(), IF);
    return visitChildren(ctx);
  }

  /**
   * Length-preserving blanking with a literal prefix. Mirrors upstream's private
   * {@code IdmsVisitor.addReplacementContext(ctx, prefix)}; duplicated here rather than widening the
   * upstream method to {@code protected} because this file is fork-added and so costs nothing on the
   * fork's upstream-delta metric, whereas widening would add a patched line to an upstream file.
   */
  private void blankWithPrefix(ParserRuleContext ctx, String prefix) {
    String newText =
        prefix
            + context
                .getExtendedDocument()
                .toString()
                .substring(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1)
                .replaceAll("[^ \n]", CobolDialect.FILLER);
    // Preserve a trailing dot: the COBOL parser does not expect dots to be consumed by the IDMS
    // preprocessor.
    if (ctx.getStop().getType() == DOT_FS) {
      newText = newText.substring(0, newText.length() - 1) + ".";
    }
    context.getExtendedDocument().replace(DialectUtils.constructRange(ctx), newText);
  }
}
