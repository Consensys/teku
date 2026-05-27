/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TekuTest {

  private static final String OPTION = "--Xnetty-max-direct-memory";
  private static final String YAML_KEY = "Xnetty-max-direct-memory";
  private static final Function<String, String> NO_ENV = name -> null;

  // --- findCliOption ---------------------------------------------------------

  @Test
  void findCliOption_returnsValueFromEqualsForm() {
    final String[] args = {"--p2p-port=9000", OPTION + "=128", "--metrics-enabled"};
    assertThat(Teku.findCliOption(args, OPTION)).contains("128");
  }

  @Test
  void findCliOption_returnsValueFromSpaceSeparatedForm() {
    final String[] args = {"--p2p-port", "9000", OPTION, "64", "--metrics-enabled"};
    assertThat(Teku.findCliOption(args, OPTION)).contains("64");
  }

  @Test
  void findCliOption_isEmptyWhenAbsent() {
    final String[] args = {"--p2p-port=9000", "--metrics-enabled"};
    assertThat(Teku.findCliOption(args, OPTION)).isEmpty();
  }

  @Test
  void findCliOption_isEmptyWhenSpaceFormHasNoFollowingValue() {
    final String[] args = {"--p2p-port=9000", OPTION};
    assertThat(Teku.findCliOption(args, OPTION)).isEmpty();
  }

  @Test
  void findCliOption_doesNotMatchPrefixOfOtherOption() {
    final String[] args = {OPTION + "-extra=99", "--metrics-enabled"};
    assertThat(Teku.findCliOption(args, OPTION)).isEmpty();
  }

  // --- megabytesToBytes ------------------------------------------------------

  @Test
  void megabytesToBytes_convertsIntegerMb() {
    assertThat(Teku.megabytesToBytes("128")).isEqualTo(128L * 1024 * 1024);
    assertThat(Teku.megabytesToBytes("1")).isEqualTo(1024L * 1024);
    assertThat(Teku.megabytesToBytes(" 64 ")).isEqualTo(64L * 1024 * 1024);
  }

  @Test
  void megabytesToBytes_rejectsNonIntegerInput() {
    assertThatThrownBy(() -> Teku.megabytesToBytes("128MB"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Teku.megabytesToBytes(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Teku.megabytesToBytes("128.5"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void megabytesToBytes_rejectsZeroAndNegative() {
    assertThatThrownBy(() -> Teku.megabytesToBytes("0"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Teku.megabytesToBytes("-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- findConfigFile --------------------------------------------------------

  @Test
  void findConfigFile_returnsFileFromLongFlag(@TempDir final Path tmp) throws IOException {
    final Path yaml = writeYaml(tmp, "p2p-port: 9001\n");
    final Optional<File> result =
        Teku.findConfigFile(new String[] {"--config-file=" + yaml}, NO_ENV);
    assertThat(result).map(File::toPath).contains(yaml);
  }

  @Test
  void findConfigFile_returnsFileFromShortFlag(@TempDir final Path tmp) throws IOException {
    final Path yaml = writeYaml(tmp, "p2p-port: 9001\n");
    final Optional<File> result = Teku.findConfigFile(new String[] {"-c", yaml.toString()}, NO_ENV);
    assertThat(result).map(File::toPath).contains(yaml);
  }

  @Test
  void findConfigFile_fallsBackToEnvVar(@TempDir final Path tmp) throws IOException {
    final Path yaml = writeYaml(tmp, "p2p-port: 9001\n");
    final Function<String, String> env =
        name -> "TEKU_CONFIG_FILE".equals(name) ? yaml.toString() : null;
    final Optional<File> result = Teku.findConfigFile(new String[] {}, env);
    assertThat(result).map(File::toPath).contains(yaml);
  }

  @Test
  void findConfigFile_isEmptyWhenNoneProvided() {
    assertThat(Teku.findConfigFile(new String[] {"--p2p-port=9001"}, NO_ENV)).isEmpty();
  }

  @Test
  void findConfigFile_isEmptyWhenPathDoesNotExist() {
    final Optional<File> result =
        Teku.findConfigFile(new String[] {"--config-file=/nonexistent/teku.yml"}, NO_ENV);
    assertThat(result).isEmpty();
  }

  // --- readYamlEntry ---------------------------------------------------------

  @Test
  void readYamlEntry_returnsValueWhenKeyPresent(@TempDir final Path tmp) throws IOException {
    final Path yaml = writeYaml(tmp, YAML_KEY + ": 128\np2p-port: 9001\n");
    assertThat(Teku.readYamlEntry(yaml.toFile(), YAML_KEY)).contains("128");
  }

  @Test
  void readYamlEntry_isEmptyWhenKeyMissing(@TempDir final Path tmp) throws IOException {
    final Path yaml = writeYaml(tmp, "p2p-port: 9001\n");
    assertThat(Teku.readYamlEntry(yaml.toFile(), YAML_KEY)).isEmpty();
  }

  @Test
  void readYamlEntry_isEmptyOnMalformedYaml(@TempDir final Path tmp) throws IOException {
    final Path yaml = tmp.resolve("broken.yml");
    Files.writeString(yaml, "not: valid: yaml: ::: [\n");
    assertThat(Teku.readYamlEntry(yaml.toFile(), YAML_KEY)).isEmpty();
  }

  private static Path writeYaml(final Path dir, final String content) throws IOException {
    final Path file = dir.resolve("teku-config.yml");
    Files.writeString(file, content);
    return file;
  }
}
