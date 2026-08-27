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
 * <p>Three of the four overrides record and delegate to {@code super}. The fourth,
 * {@link #visitIdmsStatements}, cannot delegate for one of its two branches: when an IDMS statement
 * carries a trailing COBOL imperative statement (the {@code ON} path-status form) upstream calls
 * {@code addReplacementImperativeStatementContext}, which uses {@code ExtendedDocument.clear(range)}
 * plus the literal text {@code "IF 1 + 1 = 2"}. {@code clear} bottoms out in
 * {@code ExtendedTextLine.clear(int, int)}, which writes a plain space rather than
 * {@link CobolDialect#FILLER}, so that path leaves no filler run for the COBOL parser to turn into a
 * {@code dialectNodeFiller} — and therefore no anchor to graft onto. This class blanks that case
 * itself with FILLER, prefixing {@code _IF_ } so the COBOL parser still matches a
 * {@code dialectIfStatment} and the trailing imperative statement stays reachable.
 *
 * <p>{@code visitObtainLRStatement} and {@code visitEraseStoreModifyLrStatementsOptions} are
 * deliberately <em>not</em> overridden, even though they substitute: they use the same
 * space-clearing path, produce no filler anchor, and were never recorded by the fork either.
 * Recording them would create fragments the graft step never claims.
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
