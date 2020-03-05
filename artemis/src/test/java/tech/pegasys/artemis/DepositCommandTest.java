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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;

@ExtendWith(MockitoExtension.class)
class DepositCommandTest {
  private static final int VALIDATORS_COUNT = 2;
  @Mock private DepositCommand.CommonParams commonParams;
  @Mock private DepositTransactionSender depositTransactionSender;
  @Spy private DepositCommand depositCommand = new DepositCommand();

  @Test
  void encryptedKeystoresAreCreated(@TempDir final Path tempDir) throws Exception {
    doReturn(depositTransactionSender).when(commonParams).createTransactionSender();
    doNothing().when(depositCommand).waitForTransactionReceipts(any());
    doNothing().when(depositCommand).exit(0);

    depositCommand.generate(commonParams, VALIDATORS_COUNT, "", true, tempDir.toString());

    // assert that sub directories exist
    final File[] subDirectories =
        IntStream.range(1, VALIDATORS_COUNT + 1)
            .mapToObj(i -> tempDir.resolve("validator_" + i).toFile())
            .toArray(File[]::new);
    Assertions.assertThat(tempDir.toFile().listFiles()).containsExactlyInAnyOrder(subDirectories);

    for (int i = 0; i < subDirectories.length; i++) {
      assertKeyStoreAndPasswordExist(subDirectories[i].toPath(), i + 1);
    }
  }

  private void assertKeyStoreAndPasswordExist(final Path parentDir, final int suffix) {
    final Path keystore1File = parentDir.resolve("validator_keystore_" + suffix + ".json");
    final Path password1File = parentDir.resolve("validator_password_" + suffix + ".txt");
    final Path keystore2File = parentDir.resolve("withdrawal_keystore_" + suffix + ".json");
    final Path password2File = parentDir.resolve("withdrawal_password_" + suffix + ".txt");

    Assertions.assertThat(parentDir.toFile().listFiles())
        .containsExactlyInAnyOrder(
            keystore1File.toFile(),
            password1File.toFile(),
            keystore2File.toFile(),
            password2File.toFile());
  }
}
