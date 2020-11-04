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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import tech.pegasys.teku.bls.BLSKeyPair;

public class YamlKeysWriter implements KeysWriter {
  private final Path outputPath;
  private final PrintStream standardOut;

  public YamlKeysWriter(final Path outputPath) {
    this.outputPath = outputPath;
    this.standardOut = outputPath == null ? System.out : null;
  }

  @VisibleForTesting
  YamlKeysWriter(final PrintStream standardOut) {
    this.outputPath = null;
    this.standardOut = standardOut;
  }

  @Override
  public void writeKeys(final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey)
      throws UncheckedIOException {
    final String yamlLine = getYamlFormattedString(validatorKey, withdrawalKey);
    if (outputPath == null) {
      standardOut.print(yamlLine);
    } else {
      try {
        Files.writeString(
            outputPath,
            yamlLine,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND);
      } catch (IOException e) {
        STATUS_LOG.validatorDepositYamlKeyWriterFailure(outputPath);
        throw new UncheckedIOException(e);
      }
    }
  }

  private String getYamlFormattedString(
      final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey) {
    return String.format(
        "- {privkey: '%s', pubkey: '%s', withdrawalPrivkey: '%s', withdrawalPubkey: '%s'}%n",
        validatorKey.getSecretKey().toBytes(),
        validatorKey.getPublicKey().toBytesCompressed(),
        withdrawalKey.getSecretKey().toBytes(),
        withdrawalKey.getPublicKey().toBytesCompressed());
  }
}
