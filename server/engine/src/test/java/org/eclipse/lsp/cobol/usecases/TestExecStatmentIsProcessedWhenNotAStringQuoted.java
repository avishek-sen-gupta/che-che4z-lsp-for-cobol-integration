/*
 * Copyright (c) 2025 Broadcom.
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
package org.eclipse.lsp.cobol.usecases;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.lsp.cobol.test.engine.UseCaseEngine;
import org.junit.jupiter.api.Test;

/** Test exec statement inside a quoted string is not considered as implicit dialect */
public class TestExecStatmentIsProcessedWhenNotAStringQuoted {
  public static final String TEXT_EXEC_SQL =
      "       IDENTIFICATION DIVISION.\n"
          + "       PROGRAM-ID. test12.\n"
          + "       ENVIRONMENT DIVISION.\n"
          + "       DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 {$*WERR} pic x.\n"
          + "        LINKAGE SECTION.\n"
          + "       PROCEDURE DIVISION.\n"
          + "           DISPLAY \"CHECK EXEC SQL STATE\".\n"
          + "             STOP RUN.";

  public static final String TEXT_EXEC_CICS =
      "       IDENTIFICATION DIVISION.\n"
          + "       PROGRAM-ID. test12.\n"
          + "       ENVIRONMENT DIVISION.\n"
          + "       DATA DIVISION.\n"
          + "        WORKING-STORAGE SECTION.\n"
          + "        01 {$*WERR} pic x.\n"
          + "        LINKAGE SECTION.\n"
          + "       PROCEDURE DIVISION.\n"
          + "           DISPLAY \"CHECK EXEC CICS STATE\".\n"
          + "             STOP RUN.";

  @Test
  void testExecSqlStringQuoted() {
    UseCaseEngine.runTest(TEXT_EXEC_SQL, ImmutableList.of(), ImmutableMap.of());
  }

  @Test
  void testExecCicsStringQuoted() {
    UseCaseEngine.runTest(TEXT_EXEC_CICS, ImmutableList.of(), ImmutableMap.of());
  }
}
