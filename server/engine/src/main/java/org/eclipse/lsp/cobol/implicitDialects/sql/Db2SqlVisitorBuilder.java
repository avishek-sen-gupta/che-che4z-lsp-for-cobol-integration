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

import org.eclipse.lsp.cobol.common.copybook.CopybookService;
import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.message.MessageService;

/**
 * Db2SqlVisitorBuilder
 */
public interface Db2SqlVisitorBuilder {
    /**
     *
     * @param context
     * @param messageService
     * @param copybookService
     * @param isSqlProcessingEnabled
     * @return Db2SqlVisitor
     */
    Db2SqlVisitor visitor(DialectProcessingContext context, MessageService messageService, CopybookService copybookService, boolean isSqlProcessingEnabled);
    Db2SqlVisitorBuilder ORIGINAL = Db2SqlVisitor::new;
    Db2SqlVisitorBuilder SUBSTITUTING = Db2SqlSubstitutingVisitor::new;
}
