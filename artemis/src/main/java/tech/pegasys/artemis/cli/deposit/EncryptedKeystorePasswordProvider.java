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

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

public class EncryptedKeystorePasswordProvider {
  private final String password;
  private final String environmentVariable;
  private final File passwordFile;
  private final String errorPrefix;

  private final CommandSpec spec;

  public EncryptedKeystorePasswordProvider(
      final CommandSpec spec,
      final String password,
      final String environmentVariable,
      final File passwordFile,
      final String errorPrefix) {
    this.spec = spec;
    this.password = password;
    this.environmentVariable = environmentVariable;
    this.passwordFile = passwordFile;
    this.errorPrefix = errorPrefix;
  }

  public String retrievePassword() {
    if (!isBlank(password)) {
      return password;
    }

    if (environmentVariable != null) {
      final String password = System.getenv(environmentVariable);
      if (isBlank(password)) {
        throw new ParameterException(
            spec.commandLine(),
            "Error in reading password from environment variable: " + environmentVariable);
      }
      return password;
    }

    if (passwordFile != null) {
      try {
        final String password =
            Files.asCharSource(passwordFile, StandardCharsets.UTF_8).readFirstLine();
        if (isBlank(password)) {
          throw new ParameterException(
              spec.commandLine(), "Empty password read from password file: " + passwordFile);
        }
        return password;
      } catch (final FileNotFoundException e) {
        throw new ParameterException(
            spec.commandLine(), "Password file not found: " + passwordFile);
      } catch (IOException e) {
        throw new ParameterException(
            spec.commandLine(),
            "Unexpected IO error while reading password from file ["
                + passwordFile
                + "] : "
                + e.getMessage());
      }
    }

    final String errorMessage =
        String.format(
            "Missing options: [--%1$s-password | --%1$s-password:env | --%1$s-password:file]",
            errorPrefix);
    throw new ParameterException(spec.commandLine(), errorMessage);
  }
}
