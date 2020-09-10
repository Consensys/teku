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

package tech.pegasys.teku.validator.client.loader;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.validator.client.loader.KeystoreLocker.deleteIfStaleLockfileExists;
import static tech.pegasys.teku.validator.client.loader.KeystoreLocker.longPidToNativeByteArray;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class KeystoreLockerTest {

  private KeystoreLocker keystoreLocker = new KeystoreLocker();

  @Test
  void shouldLockKeystoreFileAndFailWhenTryingCreateLockForLockedFile() throws Exception {
    final Path keystoreFile = Path.of(Resources.getResource("scryptTestVector.json").toURI());
    deletePastLockfile(keystoreFile);

    Assertions.assertThatCode(() -> keystoreLocker.lockKeystoreFile(keystoreFile))
        .doesNotThrowAnyException();
    Assertions.assertThatThrownBy(() -> keystoreLocker.lockKeystoreFile(keystoreFile))
        .isExactlyInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  void doNotDeleteLockfileIfTheProcessIsAlive() throws Exception {
    final Path keystoreFile = Path.of(Resources.getResource("lockfileTest1.json").toURI());
    deletePastLockfile(keystoreFile);

    Process process = new ProcessBuilder("/bin/sleep", "5").start();
    long pid = process.pid();
    assertThat(process.isAlive()).isTrue();
    createLockfileWithContent(keystoreFile, longPidToNativeByteArray(pid));
    assertThat(deleteIfStaleLockfileExists(keystoreFile)).isFalse();
  }

  @Test
  void deleteLockfileIfTheProcessIsNotAlive() throws Exception {
    final Path keystoreFile = Path.of(Resources.getResource("lockfileTest2.json").toURI());
    deletePastLockfile(keystoreFile);

    Process process = new ProcessBuilder("/bin/sleep", "1").start();
    Thread.sleep(2000);
    long pid = process.pid();
    assertThat(process.isAlive()).isFalse();
    createLockfileWithContent(keystoreFile, longPidToNativeByteArray(pid));
    assertThat(deleteIfStaleLockfileExists(keystoreFile)).isTrue();
  }

  @Test
  void doNotDeleteIfLockfileIsEmpty() throws Exception {
    final Path keystoreFile = Path.of(Resources.getResource("lockfileTest3.json").toURI());
    deletePastLockfile(keystoreFile);

    createLockfileWithContent(keystoreFile, new byte[0]);
    assertThat(deleteIfStaleLockfileExists(keystoreFile)).isFalse();
  }

  @Test
  void doNotDeleteIfLockfileContainsSomethingThatIsNotLong() throws Exception {
    final Path keystoreFile = Path.of(Resources.getResource("lockfileTest3.json").toURI());
    deletePastLockfile(keystoreFile);

    createLockfileWithContent(keystoreFile, new byte[Long.BYTES + 1]);
    assertThat(deleteIfStaleLockfileExists(keystoreFile)).isFalse();
  }

  private void createLockfileWithContent(final Path keystoreFile, final byte[] content)
      throws IOException {
    Files.write(Path.of(keystoreFile.toString() + ".lock"), content, CREATE_NEW);
  }

  private void deletePastLockfile(final Path keystoreFile) throws IOException {
    Files.deleteIfExists(Path.of(keystoreFile.toString() + ".lock"));
  }
}
