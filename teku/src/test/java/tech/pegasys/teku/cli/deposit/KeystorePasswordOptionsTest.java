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

package tech.pegasys.teku.cli.deposit;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class KeystorePasswordOptionsTest {
  private static final String PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final String SPACE_ENV_VARIABLE = "SPACE_ENV";
  private static final Function<String, String> envSupplier =
      envVariable -> {
        switch (envVariable) {
          case EXPECTED_ENV_VARIABLE:
            return PASSWORD;
          case SPACE_ENV_VARIABLE:
            return "     ";
          default:
            return null;
        }
      };
  private static final String EXPECTED_ENV_ERROR =
      "Error: Password cannot be read from environment variable: %s";
  private static final String EXPECTED_EMPTY_PASSWORD_FILE_ERROR =
      "Error: Empty password from file: %s";
  private final CommandLine commandLine = mock(CommandLine.class);

  @Test
  void passwordCanBeReadFromFileWithSingleLine(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    Files.writeString(passwordFile, PASSWORD);

    final String actualPassword =
        KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile());
    assertThat(actualPassword).isEqualTo(PASSWORD);
  }

  @Test
  void passwordCanBeReadFromFileWithMultipleLines(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    Files.writeString(passwordFile, PASSWORD + lineSeparator());
    Files.writeString(passwordFile, "secondline", StandardOpenOption.APPEND);

    final String actualPassword =
        KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile());
    assertThat(actualPassword).isEqualTo(PASSWORD);
  }

  @Test
  void emptyPasswordFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    final String expectedPassword = "";
    Files.writeString(passwordFile, expectedPassword + lineSeparator());

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage(EXPECTED_EMPTY_PASSWORD_FILE_ERROR, passwordFile.toFile());
  }

  @Test
  void whitespaceCharactersOnlyPasswordFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    final String expectedPassword = "    ";
    Files.writeString(passwordFile, expectedPassword + lineSeparator());

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage(EXPECTED_EMPTY_PASSWORD_FILE_ERROR, passwordFile.toFile());
  }

  @Test
  void nonExistentPasswordFileThrowsException(@TempDir final Path tempDir) {
    final Path passwordFile = tempDir.resolve("password.txt");
    final CommandLine commandLine = mock(CommandLine.class);

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage("Error: File not found: " + passwordFile);
  }

  @Test
  void passwordCanBeReadFromEnvironmentSupplierFunction() {
    final CommandLine commandLine = mock(CommandLine.class);
    final String actualPassword =
        KeystorePasswordOptions.readFromEnvironmentVariable(
            commandLine, envSupplier, EXPECTED_ENV_VARIABLE);
    assertThat(actualPassword).isEqualTo(PASSWORD);
  }

  @Test
  void nonExistentEnvironmentVariableThrowsException() {
    final CommandLine commandLine = mock(CommandLine.class);
    final String TEST_ENV = "NOT_VALID";
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                KeystorePasswordOptions.readFromEnvironmentVariable(
                    commandLine, envSupplier, TEST_ENV))
        .withMessage(EXPECTED_ENV_ERROR, TEST_ENV);
  }

  @Test
  void emptyPasswordFromEnvironmentVariableThrowsException() {
    final CommandLine commandLine = mock(CommandLine.class);
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                KeystorePasswordOptions.readFromEnvironmentVariable(
                    commandLine, envSupplier, SPACE_ENV_VARIABLE))
        .withMessage(EXPECTED_ENV_ERROR, SPACE_ENV_VARIABLE);
  }
}
