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
 *
 *   Broadcom, Inc. - initial API and implementation
 */
package com.ca.lsp.cobol.service;

import static com.ca.lsp.cobol.service.utils.WorkspaceFileService.isFileUnderExtendedSourceFolder;

/** This enum class used to track text synchronization type for the processed document. */
public enum CopybookProcessingMode {
  ENABLED,
  DISABLED;

  /**
   * This method verify if enable or disable copybook management based on two factors: 1) the file
   * is an extended source file, 2) the file is open in DID_OPEN|DID_CHANGE
   *
   * @param uri of the document opened in the editor by the client
   * @param textDocSyncType define the document sync mode that could be [{@link
   *     TextDocumentSyncType#DID_OPEN}|{@link TextDocumentSyncType#DID_CHANGE}]
   * @return A value of that could be [{@link CopybookProcessingMode#ENABLED}|{@link
   *     CopybookProcessingMode#DISABLED}]
   */
  public static CopybookProcessingMode getCopybookProcessingMode(
      String uri, String textDocSyncType) {

    return textDocSyncType.equals("DID_CHANGE")
        ? DISABLED
        : getCopybookProcessingModeByDocSync(uri);
  }

  private static CopybookProcessingMode getCopybookProcessingModeByDocSync(String uri) {
    return isFileUnderExtendedSourceFolder(uri) ? DISABLED : ENABLED;
  }
}