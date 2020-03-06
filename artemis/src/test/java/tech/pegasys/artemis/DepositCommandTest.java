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

package tech.pegasys.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;

class DepositCommandTest {
  private static final int VALIDATORS_COUNT = 2;
  private DepositCommand.CommonParams commonParams;
  private DepositTransactionSender depositTransactionSender;
  private final DepositCommand depositCommand = new DepositCommand(exitCode -> {});

  @BeforeEach
  void setUp() {
    commonParams = mock(DepositCommand.CommonParams.class);
    depositTransactionSender = mock(DepositTransactionSender.class);

    when(commonParams.createTransactionSender()).thenReturn(depositTransactionSender);
    when(depositTransactionSender.sendDepositTransaction(any(), any(), any()))
        .thenReturn(completedFuture(null));
  }

  @Test
  void encryptedKeystoresAreCreated(@TempDir final Path tempDir) {
    depositCommand.generate(commonParams, VALIDATORS_COUNT, "", true, tempDir.toString());

    // assert that sub directories exist
    final File[] subDirectories =
        IntStream.range(1, VALIDATORS_COUNT + 1)
            .mapToObj(i -> tempDir.resolve("validator_" + i).toFile())
            .toArray(File[]::new);
    assertThat(tempDir.toFile().listFiles()).containsExactlyInAnyOrder(subDirectories);

    for (int i = 0; i < subDirectories.length; i++) {
      assertKeyStoreAndPasswordExist(subDirectories[i].toPath(), i + 1);
    }
  }

  private void assertKeyStoreAndPasswordExist(final Path parentDir, final int suffix) {
    final Path keystore1File = parentDir.resolve("validator_keystore_" + suffix + ".json");
    final Path password1File = parentDir.resolve("validator_password_" + suffix + ".txt");
    final Path keystore2File = parentDir.resolve("withdrawal_keystore_" + suffix + ".json");
    final Path password2File = parentDir.resolve("withdrawal_password_" + suffix + ".txt");

    assertThat(parentDir.toFile().listFiles())
        .containsExactlyInAnyOrder(
            keystore1File.toFile(),
            password1File.toFile(),
            keystore2File.toFile(),
            password2File.toFile());
  }
}
