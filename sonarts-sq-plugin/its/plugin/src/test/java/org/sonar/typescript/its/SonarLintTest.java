/*
 * SonarTS
 * Copyright (C) 2017-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonar.typescript.its;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SonarLintTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  private StandaloneSonarLintEngine sonarlintEngine;
  private File projectDir;

  private List<String> logs = new ArrayList<>();

  @Before
  public void prepare() throws Exception {
    projectDir = temp.newFolder();
    Tests.runNPMInstall(projectDir,"typescript", "--no-save");
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugin(Tests.PLUGIN_LOCATION.getFile().toURI().toURL())
      .setSonarLintUserHome(temp.newFolder().toPath())
      .setLogOutput((formattedMessage, level) -> logs.add(formattedMessage))
      .setExtraProperties(ImmutableMap.of("sonar.typescript.internal.typescriptLocation", new File(projectDir, "node_modules").getAbsolutePath()))
      .build();
    sonarlintEngine = new StandaloneSonarLintEngineImpl(config);
  }

  @Test
  public void test() throws Exception {
    Files.write(projectDir.toPath().resolve("tsconfig.json"), "{}".getBytes(StandardCharsets.UTF_8));
    ClientInputFile inputFile = prepareInputFile("foo.ts",
      "function foo() {\n" +
        "    let x = 4; \n" +
        "    if (x = 5) {}\n" +
        "}");

    final List<Issue> issues = new ArrayList<>();

    StandaloneAnalysisConfiguration standaloneAnalysisConfiguration = new StandaloneAnalysisConfiguration(projectDir.toPath(), temp.newFolder().toPath(), Collections.singletonList
      (inputFile), ImmutableMap.of());
    AnalysisResults results = sonarlintEngine.analyze(standaloneAnalysisConfiguration, issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("typescript:S2589", 3, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S1854", 3, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S1854", 2, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S108", 3, inputFile.getPath(), "MAJOR"));

    assertThat(logs).contains("SonarTS Server is started", "Started SonarTS Analysis");
    assertThat(results.failedAnalysisFiles()).isEmpty();
  }

  @Test
  public void reportParsingError() throws Exception {
    Files.write(projectDir.toPath().resolve("tsconfig.json"), "{}".getBytes(StandardCharsets.UTF_8));
    ClientInputFile inputFile = prepareInputFile("foo.ts",
      "function foo() {\n" +
        "    let x = 4; \n" +
        "    if (x = \n" +
        "}");

    final List<Issue> issues = new ArrayList<>();

    StandaloneAnalysisConfiguration standaloneAnalysisConfiguration = new StandaloneAnalysisConfiguration(projectDir.toPath(), temp.newFolder().toPath(),
      Collections.singletonList(inputFile), ImmutableMap.of());
    AnalysisResults results = sonarlintEngine.analyze(standaloneAnalysisConfiguration, issues::add, null, null);
    assertThat(results.failedAnalysisFiles()).containsExactly(inputFile);
  }

  @Test
  public void withoutTSConfig() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.ts",
      "function foo() {\n" +
        "    let x = 4; \n" +
        "    if (x = 5) {}\n" +
        "}");

    final List<Issue> issues = new ArrayList<>();

    StandaloneAnalysisConfiguration standaloneAnalysisConfiguration = new StandaloneAnalysisConfiguration(projectDir.toPath(), temp.newFolder().toPath(), Collections.singletonList
      (inputFile), ImmutableMap.of());
    AnalysisResults results = sonarlintEngine.analyze(standaloneAnalysisConfiguration, issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("typescript:S2589", 3, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S1854", 3, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S1854", 2, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S108", 3, inputFile.getPath(), "MAJOR"));

    assertThat(logs).contains("SonarTS Server is started", "Started SonarTS Analysis");
    assertThat(logs).filteredOn(log -> log.startsWith("No tsconfig.json file found for") && log.endsWith("using default configuration")).hasSize(1);

    assertThat(results.failedAnalysisFiles()).isEmpty();
  }

  @Test
  public void withTSConfigProperty() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.ts",
      "function foo() {\n" +
        "    let x = 4; \n" +
        "    if (x = 5) {}\n" +
        "}");
    ClientInputFile tsConfigFile = prepareInputFile("tsconfig.custom.json",
      "{}");
    final List<Issue> issues = new ArrayList<>();

    StandaloneAnalysisConfiguration standaloneAnalysisConfiguration = new StandaloneAnalysisConfiguration(projectDir.toPath(), temp.newFolder().toPath(), Arrays.asList
      (inputFile, tsConfigFile), ImmutableMap.of(
      "sonar.typescript.tsconfigPath", "tsconfig.custom.json"
    ));
    AnalysisResults results = sonarlintEngine.analyze(standaloneAnalysisConfiguration, issues::add, null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("typescript:S2589", 3, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S1854", 3, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S1854", 2, inputFile.getPath(), "MAJOR"),
      tuple("typescript:S108", 3, inputFile.getPath(), "MAJOR"));

    assertThat(logs).contains("SonarTS Server is started", "Started SonarTS Analysis");
    assertThat(logs).filteredOn(log -> log.startsWith("No tsconfig.json file found for") && log.endsWith("using default configuration")).hasSize(0);

    assertThat(results.failedAnalysisFiles()).isEmpty();
  }

  private ClientInputFile prepareInputFile(String filename, String content) throws IOException {
    Path filePath = projectDir.toPath().resolve(filename).toAbsolutePath();
    Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
    return new ClientInputFile() {
      @Override
      public String getPath() {
        return filePath.toString();
      }

      @Override
      public boolean isTest() {
        return false;
      }

      @CheckForNull
      @Override
      public Charset getCharset() {
        return StandardCharsets.UTF_8;
      }

      @Override
      public <G> G getClientObject() {
        return null;
      }

      @Override
      public InputStream inputStream() throws IOException {
        return Files.newInputStream(filePath);
      }

      @Override
      public String contents() throws IOException {
        return content;
      }
    };
  }
}


