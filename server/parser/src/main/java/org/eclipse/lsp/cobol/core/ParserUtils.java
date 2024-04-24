/*
 * Copyright (c) 2024 Broadcom.
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
 *    DAF Trucks NV – implementation of DaCo COBOL statements
 *    and DAF development standards
 *
 */
package org.eclipse.lsp.cobol.core;

/**
 * Parser related utils.
 */
public final class ParserUtils {
  private ParserUtils() {
    // no-op
  }
  private static final String HW_PARSER = "hw.parser";

  public static boolean isHwParserEnabled() {
    return true;
//    return System.getenv(HW_PARSER) != null ||  System.getProperty(HW_PARSER) != null;
  }
}
