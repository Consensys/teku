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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

public class KeyStoreFilesLocatorTest {
  private static final String PATH_SEP = System.getProperty("path.separator");

  @Test
  public void shouldFindPairsAtDepth(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, Path.of("key", "1", "2", "3"), Path.of("pass", "1", "2", "3"));
    createFiles(
        tempDir,
        Path.of("key", "a.json"),
        Path.of("pass", "a.txt"),
        Path.of("key", "1", "2", "3", "b.json"),
        Path.of("pass", "1", "2", "3", "b.txt"));
    final String p1 = generatePath(tempDir, PATH_SEP, "key", "pass");
    final KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);
    locator.parse();

    assertThat(locator.getFilePairs())
        .containsExactlyInAnyOrder(
            tuple(
                tempDir, Path.of("key", "a.json").toString(), Path.of("pass", "a.txt").toString()),
            tuple(
                tempDir,
                Path.of("key", "1", "2", "3", "b.json").toString(),
                Path.of("pass", "1", "2", "3", "b.txt").toString()));
  }

  @Test
  public void shouldFindMissingPasswordAtDepth(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, Path.of("key", "1", "2", "3"), Path.of("pass", "1", "2", "3"));
    createFiles(
        tempDir,
        Path.of("key", "a.json"),
        Path.of("pass", "a.txt"),
        Path.of("key", "1", "2", "3", "b.json"));
    final String p1 = generatePath(tempDir, PATH_SEP, "key", "pass");
    final KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);

    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldFindKeyPairOfFiles(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, Path.of("key", "a"), Path.of("pass", "a.txt"));
    final String p1 =
        generatePath(tempDir, PATH_SEP, List.of("key", "a"), List.of("pass", "a.txt"));
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);
    locator.parse();

    assertThat(locator.getFilePairs())
        .containsExactly(
            tuple(tempDir, Path.of("key", "a").toString(), Path.of("pass", "a.txt").toString()));
  }

  @Test
  public void shouldIgnoreSomeFiles(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(
        tempDir, Path.of("key", "ignored"), Path.of("key", "a.json"), Path.of("pass", "a.txt"));
    if (!SystemUtils.IS_OS_WINDOWS) {
      createFiles(tempDir, Path.of("key", ".asdf.json"), Path.of("key", ".hidden2"));
    }
    final String p1 = generatePath(tempDir, PATH_SEP, "key", "pass");
    final KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);
    locator.parse();

    assertThat(locator.getFilePairs())
        .containsExactly(
            tuple(
                tempDir, Path.of("key", "a.json").toString(), Path.of("pass", "a.txt").toString()));
  }

  @Test
  public void shouldHandleFilesAndFoldersInOneArgument(@TempDir final Path tempDir)
      throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(
        tempDir,
        Path.of("key", "a"),
        Path.of("pass", "a.txt"),
        Path.of("keyStore"),
        Path.of("password"));
    final String p1 =
        generatePath(tempDir, PATH_SEP, List.of("key", "a"), List.of("pass", "a.txt"));
    final String p2 = generatePath(tempDir, PATH_SEP, "keyStore", "password");
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1, p2), PATH_SEP);
    locator.parse();

    assertThat(locator.getFilePairs())
        .containsExactlyInAnyOrder(
            tuple(tempDir, Path.of("key", "a").toString(), Path.of("pass", "a.txt").toString()),
            tuple(tempDir, "keyStore", "password"));
  }

  @Test
  public void shouldDetectMissingPasswordFileWhenDirectoryIsPresent(@TempDir final Path tempDir)
      throws IOException {
    createFolders(tempDir, Path.of("key"), Path.of("pass", "a.txt"));
    createFiles(tempDir, Path.of("key", "a"));
    final String p1 =
        generatePath(tempDir, PATH_SEP, List.of("key", "a"), List.of("pass", "a.txt"));
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);

    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldDetectMissingPasswordFile(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, Path.of("pass", "a.txt"));
    final String p1 =
        generatePath(tempDir, PATH_SEP, List.of("key", "a"), List.of("pass", "a.txt"));
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);

    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldDetectMissingKeyFile(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, "key", "pass");
    createFiles(tempDir, Path.of("key", "a"));
    final String p1 =
        generatePath(tempDir, PATH_SEP, List.of("key", "a"), List.of("pass", "a.txt"));
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);

    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldSucceedCallingParseOnEmptyList() {
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(), PATH_SEP);
    locator.parse();
    assertThat(locator.getFilePairs()).isEmpty();
  }

  @Test
  public void shouldFailWhenSplittingTooManySeparators() {
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of("a:b:c"), PATH_SEP);
    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldFailWhenStringContainsOnlyKey() {
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of("key"), PATH_SEP);
    assertThatThrownBy(locator::parse).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldHandleOldArgs(@TempDir final Path tempDir) throws IOException {
    createFolders(tempDir, Path.of("key"), Path.of("pass"));
    createFiles(tempDir, Path.of("key", "a"), Path.of("pass", "a.txt"));
    final String tempStr = tempDir.toString();
    KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(), PATH_SEP);
    locator.parseKeyAndPasswordList(
        List.of(Path.of(tempStr, "key", "a").toString()),
        List.of(Path.of(tempStr, "pass", "a.txt").toString()));

    assertThat(locator.getFilePairs())
        .containsExactly(tuple(tempDir, List.of("key", "a"), List.of("pass", "a.txt")));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS) // creating symlinks on Win requires elevated privileges
  public void shouldHandleSymlinkedDirectories(@TempDir final Path tempDir) throws IOException {
    Path realKeyDir = Path.of("actualKey");
    Path realPassDir = Path.of("actualPass");
    createFolders(tempDir, realKeyDir, realPassDir);
    createFiles(tempDir, realKeyDir.resolve("a.json"), realPassDir.resolve("a.txt"));

    try {
      Files.createSymbolicLink(tempDir.resolve("key"), realKeyDir);
      Files.createSymbolicLink(tempDir.resolve("pass"), realPassDir);
    } catch (UnsupportedOperationException e) {
      throw new TestAbortedException("Couldn't create symlink on this system");
    }

    final String p1 = generatePath(tempDir, PATH_SEP, "key", "pass");
    final KeyStoreFilesLocator locator = new KeyStoreFilesLocator(List.of(p1), PATH_SEP);
    locator.parse();

    assertThat(locator.getFilePairs())
        .containsExactlyInAnyOrder(
            tuple(
                tempDir, Path.of("key", "a.json").toString(), Path.of("pass", "a.txt").toString()));
  }

  private void createFolders(final Path tempDir, String... paths) {
    for (String path : paths) {
      File file = tempDir.resolve(path).toFile();
      if (!file.mkdirs() && !file.isDirectory()) {
        Assertions.fail("Failed to create directory " + file);
      }
    }
  }

  private void createFolders(final Path tempDir, Path... paths) {
    for (Path path : paths) {
      File file = tempDir.resolve(path).toFile();
      if (!file.mkdirs() && !file.isDirectory()) {
        Assertions.fail("Failed to create directory " + file);
      }
    }
  }

  private void createFiles(final Path tempDir, Path... paths) throws IOException {
    for (Path path : paths) {
      File file = tempDir.resolve(path).toFile();
      file.createNewFile();
    }
  }

  private String generatePath(
      final Path tempDir, final String separator, final String key, final String pass) {
    return generatePath(tempDir, separator, List.of(key), List.of(pass));
  }

  private String generatePath(
      final Path tempDir,
      final String separator,
      final List<String> keyList,
      final List<String> passList) {
    final String tempStr = tempDir.toString();
    return String.join(
        separator,
        Path.of(tempStr, keyList.toArray(new String[0])).toString(),
        Path.of(tempStr, passList.toArray(new String[0])).toString());
  }

  private Pair<Path, Path> tuple(
      final Path tempDir, final List<String> key, final List<String> path) {
    final String tempStr = tempDir.toString();
    return Pair.of(
        Path.of(tempStr, key.toArray(new String[0])),
        Path.of(tempStr, path.toArray(new String[0])));
  }

  private Pair<Path, Path> tuple(final Path tempDir, final String k, final String p) {
    final String tempStr = tempDir.toString();
    return Pair.of(new File(tempStr, k).toPath(), new File(tempStr, p).toPath());
  }
}
