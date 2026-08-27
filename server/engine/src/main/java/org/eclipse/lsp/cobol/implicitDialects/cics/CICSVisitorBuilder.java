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

package org.eclipse.lsp.cobol.implicitDialects.cics;

import org.eclipse.lsp.cobol.common.dialects.DialectProcessingContext;
import org.eclipse.lsp.cobol.common.message.MessageService;

/**
 * Builder for switching between original and substituting visitors
 */
public interface CICSVisitorBuilder {
    /**
     * Creates the appropriate visitor
     * @param context
     * @param messageService
     * @return CICSVisitor
     */
    CICSVisitor visitor(DialectProcessingContext context, MessageService messageService);
    CICSVisitorBuilder ORIGINAL = CICSVisitor::new;
    CICSVisitorBuilder SUBSTITUTING = CicsSubstitutingVisitor::new;
}
