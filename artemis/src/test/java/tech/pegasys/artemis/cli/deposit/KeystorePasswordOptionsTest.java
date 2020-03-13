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

package tech.pegasys.artemis.cli.deposit;

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
  private static final Function<String, String> envSupplier =
      s -> EXPECTED_ENV_VARIABLE.equals(s) ? PASSWORD : null;
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
        .withMessage("Error: Empty password from file: " + passwordFile.toFile());
  }

  @Test
  void whitespaceCharactersOnlyPasswordFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    final String expectedPassword = "    ";
    Files.writeString(passwordFile, expectedPassword + lineSeparator());

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage("Error: Empty password from file: " + passwordFile.toFile());
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
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                KeystorePasswordOptions.readFromEnvironmentVariable(
                    commandLine, envSupplier, "NOT_VALID"))
        .withMessage("Error: Password cannot be read from environment variable: NOT_VALID");
  }
}
