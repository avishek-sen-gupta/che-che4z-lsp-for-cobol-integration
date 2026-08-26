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

package org.eclipse.lsp.cobol.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.net.URI;
import java.util.*;
import lombok.Value;
import org.eclipse.lsp.cobol.common.copybook.CopybookProcessingMode;
import org.eclipse.lsp.cobol.common.copybook.SQLBackend;

import static org.eclipse.lsp.cobol.common.dialects.CobolDialect.COBOL_DIALECT_JAVA_VERSION;

/**
 * This dto class is used to hold config data for analysis, such as supported features, dialects and
 * copybook configuration
 */
@Value
public class AnalysisConfig {
  CopybookProcessingMode copybookProcessingMode;
  List<String> dialects;
  boolean isCicsTranslatorEnabled;
  boolean collectAstChanges;
  SqlProcessing sqlProcessing;
  SqlDecimalComma sqlDecimalCommaAllowed;
  List<DialectRegistryItem> dialectRegistry;
  Map<String, JsonElement> dialectsSettings;
  List<String> compilerOptions = new ArrayList<>();
  boolean addCicsPlaceholder;
  boolean addDb2SqlPlaceholder;
  // Map preprocessors name to list of directives
  Map<String, List<String>> preprocessorsDirectives = new HashMap<>();
  UnusedVariableSeverity unusedVariableSeverity = new UnusedVariableSeverity();

  /**
   * Canonical constructor. Declared explicitly (rather than relying on the constructor Lombok
   * generates for {@code @Value}) because the poc fork adds the {@code addCicsPlaceholder} and
   * {@code addDb2SqlPlaceholder} fields, which must stay optional for upstream call sites.
   */
  public AnalysisConfig(
      CopybookProcessingMode copybookProcessingMode,
      List<String> dialects,
      boolean isCicsTranslatorEnabled,
      boolean collectAstChanges,
      SqlProcessing sqlProcessing,
      SqlDecimalComma sqlDecimalCommaAllowed,
      List<DialectRegistryItem> dialectRegistry,
      Map<String, JsonElement> dialectsSettings,
      boolean addCicsPlaceholder,
      boolean addDb2SqlPlaceholder) {
    this.copybookProcessingMode = copybookProcessingMode;
    this.dialects = dialects;
    this.isCicsTranslatorEnabled = isCicsTranslatorEnabled;
    this.collectAstChanges = collectAstChanges;
    this.sqlProcessing = sqlProcessing;
    this.sqlDecimalCommaAllowed = sqlDecimalCommaAllowed;
    this.dialectRegistry = dialectRegistry;
    this.dialectsSettings = dialectsSettings;
    this.addCicsPlaceholder = addCicsPlaceholder;
    this.addDb2SqlPlaceholder = addDb2SqlPlaceholder;
  }

  /** Upstream-shaped constructor: no dialect placeholder substitution. */
  public AnalysisConfig(
      CopybookProcessingMode copybookProcessingMode,
      List<String> dialects,
      boolean isCicsTranslatorEnabled,
      boolean collectAstChanges,
      SqlProcessing sqlProcessing,
      SqlDecimalComma sqlDecimalCommaAllowed,
      List<DialectRegistryItem> dialectRegistry,
      Map<String, JsonElement> dialectsSettings) {
    this(
        copybookProcessingMode,
        dialects,
        isCicsTranslatorEnabled,
        collectAstChanges,
        sqlProcessing,
        sqlDecimalCommaAllowed,
        dialectRegistry,
        dialectsSettings,
        false,
        false);
  }

  /**
   * Create the default language features config, containing all features and the given copybook
   * processing mode
   *
   * @param mode the mode of copybook processing for this analysis
   * @return the analysis configuration
   */
  public static AnalysisConfig defaultConfig(CopybookProcessingMode mode) {
    return new AnalysisConfig(
        mode,
        ImmutableList.of(),
        true,
        false,
        SqlProcessing.ENABLED,
        SqlDecimalComma.DISABLED,
        ImmutableList.of(),
        ImmutableMap.of("target-sql-backend", new Gson().toJsonTree(SQLBackend.DB2_SERVER)));
  }

  public static AnalysisConfig defaultConfig(
      CopybookProcessingMode mode, boolean collectAstChanges) {
    return new AnalysisConfig(
        mode,
        ImmutableList.of(),
        true,
        collectAstChanges,
        SqlProcessing.ENABLED,
        SqlDecimalComma.DISABLED,
        ImmutableList.of(),
        ImmutableMap.of("target-sql-backend", new Gson().toJsonTree(SQLBackend.DB2_SERVER)));
  }

  /**
   * Config used by smojol to parse IDMS programs: registers the IDMS dialect from a jar and enables
   * CICS / DB2 SQL placeholder substitution.
   *
   * @param dialectJarPath path of the jar providing the IDMS dialect
   * @param mode the mode of copybook processing for this analysis
   * @return the analysis configuration
   */
  public static AnalysisConfig idmsConfig(String dialectJarPath, CopybookProcessingMode mode) {
    return new AnalysisConfig(
        mode,
        ImmutableList.of("IDMS"),
        true,
        false,
        SqlProcessing.ENABLED,
        SqlDecimalComma.DISABLED,
        ImmutableList.of(
            new DialectRegistryItem(
                "IDMS",
                COBOL_DIALECT_JAVA_VERSION,
                URI.create(String.format("file://%s", dialectJarPath)),
                "Some Description",
                "Some ID")),
        ImmutableMap.of("target-sql-backend", new Gson().toJsonTree(SQLBackend.DB2_SERVER)),
        true,
        true);
  }

  /**
   * Config used by smojol for plain IBM COBOL: enables CICS / DB2 SQL placeholder substitution so
   * that implicit dialect fragments can be re-injected into the parse tree after parsing.
   *
   * @param mode the mode of copybook processing for this analysis
   * @return the analysis configuration
   */
  public static AnalysisConfig substitutingDefaultConfig(CopybookProcessingMode mode) {
    return new AnalysisConfig(
        mode,
        ImmutableList.of(),
        true,
        false,
        SqlProcessing.ENABLED,
        SqlDecimalComma.DISABLED,
        ImmutableList.of(),
        ImmutableMap.of("target-sql-backend", new Gson().toJsonTree(SQLBackend.DB2_SERVER)),
        true,
        true);
  }
}
