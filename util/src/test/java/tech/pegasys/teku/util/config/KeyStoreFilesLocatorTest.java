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

package tech.pegasys.teku.util.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KeyStoreFilesLocatorTest {
  @Test
  public void shouldFindPairsAtDepth(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key/1/2/3", "pass/1/2/3");
    createFiles(tempDir, "key/a.json", "pass/a.txt", "key/1/2/3/b.json", "pass/1/2/3/b.bin");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key:%s/pass", tempStr, tempStr))), ":");
    locator.parse();
    assertThat(locator.getFilePairs())
        .containsExactlyInAnyOrder(
            tuple(tempStr, "key/a.json", "pass/a.txt"),
            tuple(tempStr, "key/1/2/3/b.json", "pass/1/2/3/b.bin"));
  }

  @Test
  public void shouldFindMissingPasswordAtDepth(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key/1/2/3", "pass/1/2/3");
    createFiles(tempDir, "key/a.json", "pass/a.txt", "key/1/2/3/b.json");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key:%s/pass", tempStr, tempStr))), ":");
    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldFindKeyPairOfFiles(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, "key/a", "pass/a.txt");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key/a;%s/pass/a.txt", tempStr, tempStr))), ";");
    locator.parse();
    assertThat(locator.getFilePairs()).containsExactly(tuple(tempStr, "key/a", "pass/a.txt"));
  }

  @Test
  public void shouldIgnoreSomeFiles(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(
        tempDir, "key/.asdf.json", "key/.hidden2", "key/ignored", "key/a.json", "pass/a.txt");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key;%s/pass", tempStr, tempStr))), ";");
    locator.parse();
    assertThat(locator.getFilePairs()).containsExactly(tuple(tempStr, "key/a.json", "pass/a.txt"));
  }

  @Test
  public void shouldHandleFilesAndFoldersInOneArgument(@TempDir final Path tempDir)
      throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, "key/a", "pass/a.txt", "keyStore", "password");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(
                List.of(
                    String.format("%s/key/a:%s/pass/a.txt", tempStr, tempStr),
                    String.format("%s/keyStore:%s/password", tempStr, tempStr))),
            ":");
    locator.parse();
    assertThat(locator.getFilePairs().size()).isEqualTo(2);
  }

  @Test
  public void shouldDetectMissingPasswordFileWhenDirectoryIsPresent(@TempDir final Path tempDir)
      throws IOException {
    createFolders(tempDir, "key", "pass/a.txt");
    createFiles(tempDir, "key/a");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key/a;%s/pass/a.txt", tempStr, tempStr))), ";");
    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldDetectMissingPasswordFile(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, "pass/a.txt");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key/a:%s/pass/a.txt", tempStr, tempStr))), ":");
    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldDetectMissingKeyFile(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, "key/a");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator =
        new KeyStoreFilesLocator(
            Optional.of(List.of(String.format("%s/key/a:%s/pass/a.txt", tempStr, tempStr))), ":");
    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldSucceedCallingParseOnEmptyList() {
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(Optional.empty(), ":");
    locator.parse();
    assertThat(locator.getFilePairs()).isEmpty();
  }

  @Test
  public void shouldHandleOldArgs(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, "key/a", "pass/a.txt");
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(Optional.empty(), ":");
    locator.parseKeyAndPasswordList(List.of(tempStr + "/key/a"), List.of(tempStr + "/pass/a.txt"));
    assertThat(locator.getFilePairs())
        .containsExactly(tuple(tempDir.toString(), "key/a", "pass/a.txt"));
  }

  private void createFolders(final Path tempDir, String... paths) {
    for (String path : paths) {
      File file = new File(tempDir.toString() + File.separator + path);
      file.mkdirs();
    }
  }

  private void createFiles(final Path tempDir, String... paths) throws IOException {
    for (String path : paths) {
      File file = new File(tempDir.toString() + File.separator + path);
      file.createNewFile();
    }
  }

  private Pair<Path, Path> tuple(final String tempStr, final String k, final String p) {
    return Pair.of(
        new File(String.format("%s/%s", tempStr, k)).toPath(),
        new File(String.format("%s/%s", tempStr, p)).toPath());
  }
}
