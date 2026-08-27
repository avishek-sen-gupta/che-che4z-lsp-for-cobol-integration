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
package org.eclipse.lsp.cobol.implicitDialects.sql;

import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp.cobol.common.copybook.CopybookService;
import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.poc.LocalisedDialect;
import org.eclipse.lsp.cobol.common.poc.PersistentData;

/**
 * A {@link Db2SqlVisitor} that additionally records the extended-document region each DB2 SQL
 * construct occupied, together with its parse tree, so smojol can graft the SQL subtree back onto
 * the COBOL parse tree afterwards.
 *
 * <p>Substitution itself is entirely upstream's {@code addReplacementContext}, which is
 * length-preserving. This class only records and delegates.
 *
 * <p>The overridden set is exactly the set of rules upstream blanks out of the extended document:
 * {@code execRule}, {@code binary_host_variable}, and the seven {@code nonExecRule} host-variable
 * forms that reach {@code createHostVariableDefinitionNode}. A construct that gets blanked without
 * being recorded is unrecoverable for smojol, so this list must track upstream's.
 */
class Db2SqlSubstitutingVisitor extends Db2SqlVisitor {

  Db2SqlSubstitutingVisitor(
      DialectProcessingContext context,
      MessageService messageService,
      CopybookService copybookService,
      boolean isSqlProcessingEnabled) {
    super(context, messageService, copybookService, isSqlProcessingEnabled);
  }

  private static void record(ParserRuleContext ctx) {
    PersistentData.record(ctx, LocalisedDialect.DB2_SQL);
  }

  @Override
  public List<Node> visitExecRule(Db2SqlParser.ExecRuleContext ctx) {
    record(ctx);
    return super.visitExecRule(ctx);
  }

  @Override
  public List<Node> visitBinary_host_variable(Db2SqlParser.Binary_host_variableContext ctx) {
    record(ctx);
    return super.visitBinary_host_variable(ctx);
  }

  @Override
  public List<Node> visitBinary_host_variable_array(
      Db2SqlParser.Binary_host_variable_arrayContext ctx) {
    record(ctx);
    return super.visitBinary_host_variable_array(ctx);
  }

  @Override
  public List<Node> visitResult_set_locator_variable(
      Db2SqlParser.Result_set_locator_variableContext ctx) {
    record(ctx);
    return super.visitResult_set_locator_variable(ctx);
  }

  @Override
  public List<Node> visitTableLocators_variable(Db2SqlParser.TableLocators_variableContext ctx) {
    record(ctx);
    return super.visitTableLocators_variable(ctx);
  }

  @Override
  public List<Node> visitRowid_host_variables(Db2SqlParser.Rowid_host_variablesContext ctx) {
    record(ctx);
    return super.visitRowid_host_variables(ctx);
  }

  @Override
  public List<Node> visitRowid_host_variables_arrays(
      Db2SqlParser.Rowid_host_variables_arraysContext ctx) {
    record(ctx);
    return super.visitRowid_host_variables_arrays(ctx);
  }

  @Override
  public List<Node> visitLob_xml_host_variables(Db2SqlParser.Lob_xml_host_variablesContext ctx) {
    record(ctx);
    return super.visitLob_xml_host_variables(ctx);
  }

  @Override
  public List<Node> visitLob_host_variables(Db2SqlParser.Lob_host_variablesContext ctx) {
    record(ctx);
    return super.visitLob_host_variables(ctx);
  }

  @Override
  public List<Node> visitLob_host_variables_arrays(
      Db2SqlParser.Lob_host_variables_arraysContext ctx) {
    record(ctx);
    return super.visitLob_host_variables_arrays(ctx);
  }
}
