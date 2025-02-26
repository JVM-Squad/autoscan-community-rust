/*
 * Community Rust Plugin
 * Copyright (C) 2021-2025 Vladimir Shelkovnikov
 * mailto:community-rust AT pm DOT me
 * http://github.com/C4tWithShell/community-rust
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.elegoff.plugins.communityrust.clippy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.elegoff.plugins.communityrust.language.RustLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rules.RuleType;
import org.sonarsource.analyzer.commons.ExternalReportProvider;
import org.sonarsource.analyzer.commons.internal.json.simple.parser.ParseException;


import static org.apache.commons.lang3.StringUtils.isEmpty;

public class ClippySensor implements Sensor {

  public static final String REPORT_PROPERTY_KEY = "community.rust.clippy.reportPaths";
  static final String LINTER_KEY = "clippy";
  static final String LINTER_NAME = "Clippy";
  private static final Logger LOG = LoggerFactory.getLogger(ClippySensor.class);
  private static final Long DEFAULT_CONSTANT_DEBT_MINUTES = 5L;
  private static final int MAX_LOGGED_FILE_NAMES = 20;

  private FileAdjustor fileAdjustor;

  @CheckForNull
  private InputFile inputFile(SensorContext context, String filePath) {
    String relativePath = fileAdjustor.relativePath(filePath);
    return context.fileSystem().inputFile(context.fileSystem().predicates().hasPath(relativePath));
  }

  private void saveIssue(SensorContext context, ClippyJsonReportReader.ClippyIssue clippyIssue, Set<String> unresolvedInputFiles) {
    if (isEmpty(clippyIssue.ruleKey) || isEmpty(clippyIssue.filePath) || isEmpty(clippyIssue.message)) {
      LOG.debug("Missing information for ruleKey:'{}', filePath:'{}', message:'{}'", clippyIssue.ruleKey, clippyIssue.filePath, clippyIssue.message);
      return;
    }

    InputFile inputFile = inputFile(context, clippyIssue.filePath);

    if (inputFile == null) {
      unresolvedInputFiles.add(clippyIssue.filePath);
      return;
    }

    var newExternalIssue = context.newExternalIssue();
    newExternalIssue
      .type(RuleType.CODE_SMELL)
      .severity(toSonarQubeSeverity(clippyIssue.severity))
      .remediationEffortMinutes(DEFAULT_CONSTANT_DEBT_MINUTES);

    NewIssueLocation primaryLocation = newExternalIssue.newLocation()
      .message(clippyIssue.message)
      .on(inputFile);

    if (clippyIssue.lineNumberStart != null) {
      if (clippyIssue.lineNumberStart.equals(clippyIssue.lineNumberEnd) && clippyIssue.colNumberStart.equals(clippyIssue.colNumberEnd)) {
        TextRange range = inputFile.selectLine(clippyIssue.lineNumberStart);
        primaryLocation.at(range);
      } else {
        primaryLocation.at(inputFile.newRange(clippyIssue.lineNumberStart, clippyIssue.colNumberStart - 1, clippyIssue.lineNumberEnd, clippyIssue.colNumberEnd - 1));
      }
    }

    newExternalIssue.at(primaryLocation);
    newExternalIssue.engineId(LINTER_KEY).ruleId(clippyIssue.ruleKey);
    newExternalIssue.save();
  }

  private static org.sonar.api.batch.rule.Severity toSonarQubeSeverity(String severity) {
    if ("error".equalsIgnoreCase(severity)) {
      return Severity.MAJOR;
    } else
      return Severity.MINOR;
  }

  @Override
  public void execute(SensorContext context) {
    Set<String> unresolvedInputFiles = new HashSet<>();
    fileAdjustor = FileAdjustor.create(context);
    List<File> reportFiles = ExternalReportProvider.getReportFiles(context, reportPathKey());
    reportFiles.forEach(report -> importReport(report, context, unresolvedInputFiles));
    logUnresolvedInputFiles(unresolvedInputFiles);
  }

  private void importReport(File rawReport, SensorContext context, Set<String> unresolvedInputFiles) {
    LOG.info("Importing {}", rawReport);

    try {
      InputStream in = ClippyJsonReportReader.toJSON(rawReport);
      ClippyJsonReportReader.read(in, clippyIssue -> saveIssue(context, clippyIssue, unresolvedInputFiles));
    } catch (IOException | ParseException e) {
      LOG.error("No issues information will be saved as the report file '{}' can't be read. " +
        e.getClass().getSimpleName() + ": " + e.getMessage(), rawReport, e);
    }
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyWhenConfiguration(conf -> conf.hasKey(reportPathKey()))
      .onlyOnLanguage(RustLanguage.KEY)
      .name("Import of " + linterName() + " issues");
  }

  private void logUnresolvedInputFiles(Set<String> unresolvedInputFiles) {
    if (unresolvedInputFiles.isEmpty()) {
      return;
    }
    String fileList = unresolvedInputFiles.stream().sorted().limit(MAX_LOGGED_FILE_NAMES).collect(Collectors.joining(";"));
    if (unresolvedInputFiles.size() > MAX_LOGGED_FILE_NAMES) {
      fileList += ";...";
    }
    String linterName = linterName();
    logger().warn("Failed to resolve {} file path(s) in {} report. No issues imported related to file(s): {}", unresolvedInputFiles.size(), linterName, fileList);
  }

  protected String linterName() {
    return LINTER_NAME;
  }

  protected String reportPathKey() {
    return REPORT_PROPERTY_KEY;
  }

  protected Logger logger() {
    return LOG;
  }
}
