/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli.util;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;
import tech.pegasys.teku.cli.util.TestCommand.Subcommand;

class YamlConfigFileDefaultProviderTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));

  @Test
  void parsingValidYamlFilePopulatesCommandObject(@TempDir final Path tempDir) throws IOException {
    final Path configFile = writeToYamlConfigFile(defaultOptions(), tempDir);
    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));
    commandLine.parseArgs();
    final TestCommand testCommand = commandLine.getCommand();

    Assertions.assertThat(testCommand.getCount()).isEqualTo(10);
    Assertions.assertThat(testCommand.getNames()).containsExactlyInAnyOrder("a", "b");
    Assertions.assertThat(testCommand.isTestEnabled()).isTrue();
  }

  @Test
  void parsingEmptyConfigFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path configFile = writeToYamlConfigFile(Collections.emptyMap(), tempDir);
    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(commandLine::parseArgs)
        .withMessage("Empty yaml configuration file: %s", configFile);
  }

  @Test
  void parsingNonExistingConfigFileThrowsException(@TempDir final Path tempDir) {
    final Path configFile = tempDir.resolve("config.yaml");
    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(commandLine::parseArgs)
        .withMessage("Unable to read yaml configuration. File not found: %s", configFile);
  }

  @Test
  void parsingInvalidYamlConfigFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path configFile =
        Files.writeString(tempDir.resolve("config.yaml"), "test: test\noption= True\n");
    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(commandLine::parseArgs)
        .withMessageStartingWith(
            "Unable to read yaml configuration. Invalid yaml file [%s]:", configFile);
  }

  @Test
  void parsingSequenceMappingInYamlConfigFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Path configFile =
        Files.writeString(tempDir.resolve("config.yaml"), "- test\n- test2\n- test3");
    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(commandLine::parseArgs)
        .withMessage("Unexpected yaml content, expecting block mappings.");
  }

  @Test
  void parsingUnknownOptionsInYamlConfigFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Map<String, Object> options = defaultOptions();
    options.put("additional-option-3", "x");
    options.put("additional-option-1", "x");
    options.put("additional-option-2", "y");
    final Path configFile = writeToYamlConfigFile(options, tempDir);

    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(commandLine::parseArgs)
        .withMessage(
            "Unknown options in yaml configuration file: additional-option-1, additional-option-2, additional-option-3");
  }

  @Test
  void subcommandOptionsInYamlConfigFileAreAllowed(@TempDir final Path tempDir) throws IOException {
    final Map<String, Object> options = defaultOptions();
    options.put("subcommand-option-1", "x");
    final Path configFile = writeToYamlConfigFile(options, tempDir);

    final CommandLine commandLine = new CommandLine(TestCommand.class);
    commandLine.setDefaultValueProvider(
        new YamlConfigFileDefaultProvider(commandLine, configFile.toFile()));

    final ParseResult result = commandLine.parseArgs("subcommand");
    assertThat(result.hasSubcommand()).isTrue();

    final Subcommand subcommand = commandLine.getSubcommands().get("subcommand").getCommand();
    assertThat(subcommand.getSubcommandOption()).isEqualTo("x");
  }

  private Map<String, Object> defaultOptions() {
    final Map<String, Object> options = new HashMap<>();
    options.put("count", 10);
    options.put("names", List.of("a", "b"));
    options.put("test-enabled", true);
    return options;
  }

  private Path writeToYamlConfigFile(final Map<String, Object> options, final Path tempDir)
      throws IOException {
    final Path configFile = tempDir.resolve("config.yaml");
    objectMapper.writeValue(configFile.toFile(), options);
    return configFile;
  }
}
