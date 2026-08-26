/*
 * Copyright (c) 2021 Broadcom.
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

package org.eclipse.lsp.cobol.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileSystem;
import org.eclipse.lsp.cobol.common.file.WorkspaceFileService;
import org.junit.jupiter.api.Test;

/** This test checks the logic File Service methods that do not interact with the file system */
class WorkspaceFileServiceTest {

  private static String pathPrefix() {
    return FileSystem.WINDOWS.equals(FileSystem.getCurrent()) ? "c:/" : "/";
  }

  private static String uriPrefix() {
    return "file:///" + pathPrefix();
  }

  @Test
  void getNameFromURIWithExtension() {
    assertEquals(
        "ACTSOAPI.cbl",
        new WorkspaceFileService()
            .getNameFromURI(uriPrefix() + "workspace/POSITIVE_TESTS/positive/ACTSOAPI.cbl"));
  }

  @Test
  void getNameFromURIWithoutExtension() {
    assertEquals(
        "ACTSOAPI",
        new WorkspaceFileService()
            .getNameFromURI(uriPrefix() + "workspace/POSITIVE_TESTS/positive/ACTSOAPI"));
  }

  @Test
  void getNameFromURIEmpty() {
    assertEquals("", new WorkspaceFileService().getNameFromURI(""));
  }

  @Test
  void getNameFromURIOnlyNameWithExtension() {
    assertEquals("document.cbl", new WorkspaceFileService().getNameFromURI("document.cbl"));
  }

  @Test
  void getNameFromURIOnlyName() {
    assertEquals("document", new WorkspaceFileService().getNameFromURI("document"));
  }

  @Test
  void getNameFromURIDataset() {
    assertEquals(
        "AD1DEV.PUBLIC.COTPA01.COBOL(TSTJUST).cbl",
        new WorkspaceFileService()
            .getNameFromURI(
                uriPrefix()
                    + "workspace/POSITIVE_TESTS/.e4e/AD1DEV.PUBLIC.COTPA01.COBOL(TSTJUST).cbl"));
  }

  @Test
  void getNameFromURIWithQuery() {
    assertEquals(
        "CAWA02-version-0133ED5400EA4E17.cbl",
        new WorkspaceFileService()
            .getNameFromURI(
                "file:///c:/workspace/POSITIVE_TESTS/.e4e/CAWA02-version-0133ED5400EA4E17.cbl?%7B%22service%22:%7B%22credential%22:%7B%22"));
  }

  @Test
  void getNameFromURIWithQueryDataset() {
    assertEquals(
        "AD1DEV.PUBLIC.COTPA01.COBOL(TSTJUST).cbl",
        new WorkspaceFileService()
            .getNameFromURI(
                "file:///c:/workspace/POSITIVE_TESTS/.e4e/AD1DEV.PUBLIC.COTPA01.COBOL(TSTJUST).cbl?%7B%22service%22:%7B%22credential%22:%7B%22"));
  }

  @Test
  void getPathFromURI() {
    WorkspaceFileService workspaceFileService = new WorkspaceFileService();
    String unNormalizedURI = "file:///c:/workspace/path/wi th/spe&cial!@#chars";
    assertThrows(
        IllegalArgumentException.class, () -> workspaceFileService.getPathFromURI(unNormalizedURI));

    String normalizedURI = "file:///c:/workspace/path/wi%20th/spe%26cial%21%40%23chars";
    Path expectedPath = Paths.get("c:", "workspace", "path", "wi th", "spe&cial!@#chars");
    Path actualPath = workspaceFileService.getPathFromURI(normalizedURI);
    assert actualPath != null;
    String actualPathString = actualPath.toString();

    // adjust path as per fs
    if (actualPath.startsWith("/")) {
      actualPathString = actualPath.toString().substring(1);
    }

    assertEquals(actualPathString, expectedPath.toString());
  }
}
