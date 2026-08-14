/*
 * Copyright (c) 2020 Broadcom.
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

package org.eclipse.lsp.cobol.usecases;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.lsp.cobol.test.engine.UseCaseEngine;
import org.junit.jupiter.api.Test;

/**
 * Tests the ALTERNATE key clause under file control. The standard form is ALTERNATE RECORD KEY IS,
 * but RECORD is omitted by some compilers, so ALTERNATE KEY IS must parse as well.
 */
class TestAlternateRecordKeyClause {

  private static final String BASE =
      "       IDENTIFICATION DIVISION.\n"
          + "       PROGRAM-ID. TEST1.\n"
          + "       ENVIRONMENT DIVISION.\n"
          + "       INPUT-OUTPUT SECTION.\n"
          + "       FILE-CONTROL.\n"
          + "           SELECT {$CUSTFILE}  ASSIGN         TO CUSTDD\n"
          + "                            ORGANIZATION   IS INDEXED\n"
          + "                            RECORD KEY     IS {$CUST-ID}\n"
          + "                            ACCESS MODE    IS DYNAMIC\n";

  private static final String TAIL =
      "                            FILE STATUS    IS {$WS-FILE-STATUS}.\n"
          + "       DATA DIVISION.\n"
          + "       FILE SECTION.\n"
          + "       FD  {$*CUSTFILE}.\n"
          + "       01  {$*CUST-REC}.\n"
          + "           05 {$*CUST-ID}      PIC X(10).\n"
          + "           05 {$*CUST-ALT-ID}    PIC X(10).\n"
          + "       WORKING-STORAGE SECTION.\n"
          + "       01 {$*WS-FILE-STATUS} PIC XX.\n"
          + "       PROCEDURE DIVISION.\n"
          + "           DISPLAY 'HI'.\n";

  private static final String WITH_RECORD =
      BASE + "                            ALTERNATE RECORD KEY IS {$CUST-ALT-ID}\n" + TAIL;

  private static final String WITHOUT_RECORD =
      BASE + "                            ALTERNATE KEY  IS {$CUST-ALT-ID}\n" + TAIL;

  @Test
  void alternateRecordKeyIsParsed() {
    UseCaseEngine.runTest(WITH_RECORD, ImmutableList.of(), ImmutableMap.of());
  }

  @Test
  void alternateKeyWithoutRecordIsParsed() {
    UseCaseEngine.runTest(WITHOUT_RECORD, ImmutableList.of(), ImmutableMap.of());
  }
}
