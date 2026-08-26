/*
 * Copyright (c) 2023 Broadcom.
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

import java.util.List;
import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.poc.LocalisedDialect;
import org.eclipse.lsp.cobol.common.poc.PersistentData;

/**
 * A {@link CICSVisitor} that additionally records the extended-document region each CICS fragment
 * occupied, together with its parse tree, so smojol can graft the CICS subtree back onto the COBOL
 * parse tree afterwards.
 *
 * <p>Substitution itself is entirely upstream's: {@code visitCicsExecBlock} calls
 * {@code changeContextToDialectStatement} and {@code visitCicsDfhResp} calls
 * {@code addReplacementContext}, both length-preserving. This class only records and delegates, so
 * it inherits every upstream improvement instead of shadowing it.
 *
 * <p>Recording happens before the {@code super} call, which is safe: the range comes from the parse
 * tree, not from the document, so it is unaffected by the blanking {@code super} performs.
 *
 * <p>{@code visitCicsDfhValue} is deliberately not overridden. Upstream substitutes there and the
 * fork never recorded a fragment for it, so leaving it alone preserves existing behaviour.
 */
class CicsSubstitutingVisitor extends CICSVisitor {

  CicsSubstitutingVisitor(DialectProcessingContext context, MessageService messageService) {
    super(context, messageService);
  }

  @Override
  public List<Node> visitCicsExecBlock(CICSParser.CicsExecBlockContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.CICS);
    return super.visitCicsExecBlock(ctx);
  }

  @Override
  public List<Node> visitCicsDfhResp(CICSParser.CicsDfhRespContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.CICS);
    return super.visitCicsDfhResp(ctx);
  }
}
