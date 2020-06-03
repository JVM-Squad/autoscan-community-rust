/**
 *
 * Sonar Rust Plugin (Community)
 * Copyright (C) 2020 Eric Le Goff
 * http://github.com/elegoff/sonar-rust
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
package org.elegoff.plugins.rust;

import com.sonar.sslr.api.Grammar;
import org.elegoff.plugins.rust.language.RustLanguage;
import org.elegoff.rust.checks.CheckList;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.squidbridge.AstScanner;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.squidbridge.api.SourceCode;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.indexer.QueryByType;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Main sensor
 */
public class RustSensor implements ProjectSensor {


    /**
     * Key of the file suffix parameter
     */
    public static final String API_FILE_SUFFIXES_KEY = "sonar.rust.api.file.suffixes";

    /**
     * Default API files knows suffixes
     */
    public static final String API_DEFAULT_FILE_SUFFIXES = ".rs,.toml";


    private static final Logger LOG = Loggers.get(RustSensor.class);

    private final FileLinesContextFactory fileLinesContextFactory;
    private final RustChecks checks;
    private final NoSonarFilter noSonarFilter;

    private SensorContext context;

    public RustSensor(FileLinesContextFactory fileLinesContextFactory, CheckFactory checkFactory, NoSonarFilter noSonarFilter) {
        this.checks = RustChecks.createRustCheck(checkFactory)
                .addChecks(CheckList.REPOSITORY_KEY, CheckList.getChecks());
        this.fileLinesContextFactory = fileLinesContextFactory;
        this.noSonarFilter = noSonarFilter;
    }

    public static List<PropertyDefinition> properties() {
        return Collections.unmodifiableList(Arrays.asList(
                PropertyDefinition.builder(API_FILE_SUFFIXES_KEY)
                        .defaultValue(API_DEFAULT_FILE_SUFFIXES)
                        .name("Public API file suffixes")
                        .multiValues(true)
                        .description("Comma-separated list of suffixes for files that should be searched for API comments."
                                + " To not filter, leave the list empty.")
                        .category("Rust")
                        .subCategory("(3) Metrics")
                        .onQualifiers(Qualifiers.PROJECT)
                        .build()
        ));
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .onlyOnLanguage(RustLanguage.KEY)
                .name("Rust Sensor")
                .onlyOnFileType(InputFile.Type.MAIN);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(SensorContext context) {
        this.context = context;

        var visitors = new ArrayList<SquidAstVisitor<Grammar>>(checks.all());
        //AstScanner<Grammar> scanner = RustAstScanner.create(squidConfig, visitors.toArray(
        //        new SquidAstVisitor[visitors.size()]));

        Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(
                context.fileSystem().predicates().and(context.fileSystem().predicates().hasLanguage("Rust"),
                        context.fileSystem().predicates().hasType(InputFile.Type.MAIN)));

        var files = new ArrayList<File>();
        for (var file : inputFiles) {
            files.add(new File(file.uri().getPath()));
        }

        //scanner.scanFiles(files);

        //Collection<SourceCode> squidSourceFiles = scanner.getIndex().search(new QueryByType(SourceFile.class));
        //save(squidSourceFiles);
    }

    private void save(Collection<SourceCode> sourceCodeFiles) {
        // don't publish metrics on modules, which were not analyzed
        // otherwise hierarchical multi-module projects will contain wrong metrics ( == 0)
        // see also AggregateMeasureComputer
        if (sourceCodeFiles.isEmpty()) {
            return;
        }

        for (var sourceCodeFile : sourceCodeFiles) {
            SourceFile sourceFile = (SourceFile) sourceCodeFile;
            var ioFile = new File(sourceFile.getKey());
            InputFile inputFile = context.fileSystem().inputFile(context.fileSystem().predicates().is(ioFile));

            if (inputFile != null) {
                saveMeasures(inputFile, sourceFile);
                saveViolations(inputFile, sourceFile);
                saveFileLinesContext(inputFile, sourceFile);
            }
        }
    }

    private void saveMeasures(InputFile inputFile, SourceFile sourceFile) {
        // NOSONAR
        noSonarFilter.noSonarInFile(inputFile, sourceFile.getNoSonarTagLines());
    }

    private void saveViolations(InputFile inputFile, SourceFile sourceFile) {
        if (sourceFile.hasCheckMessages()) {
            for (var message : sourceFile.getCheckMessages()) {
                int line = 1;
                if (message.getLine() != null && message.getLine() > 0) {
                    line = message.getLine();
                }

                NewIssue newIssue = context.newIssue().forRule(
                        RuleKey.of(CheckList.REPOSITORY_KEY,
                                Objects.requireNonNull(checks.ruleKey(
                                        (SquidAstVisitor<Grammar>) message.getCheck()))
                                        .rule()));
                NewIssueLocation location = newIssue.newLocation().on(inputFile).at(inputFile.selectLine(line))
                        .message(message.getText(Locale.ENGLISH));

                newIssue.at(location);
                newIssue.save();
            }
        }


    }

    private void saveFileLinesContext(InputFile inputFile, SourceFile sourceFile) {
        // measures for the lines of file
        FileLinesContext fileLinesContext = fileLinesContextFactory.createFor(inputFile);

        fileLinesContext.save();
    }


    private <T extends Serializable> void saveMetric(InputFile file, Metric<T> metric, T value) {
        context.<T>newMeasure()
                .withValue(value)
                .forMetric(metric)
                .on(file)
                .save();
    }


    private String[] getStringLinesOption(String key) {
        Pattern EOL_PATTERN = Pattern.compile("\\R");
        Optional<String> value = context.config().get(key);
        if (value.isPresent()) {
            return EOL_PATTERN.split(value.get(), -1);
        }
        return new String[0];
    }

}