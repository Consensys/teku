/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class GraffitiParserTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final Bytes32 defaultGraffiti = Bytes32Parser.toBytes32("default graffiti");
  private final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
  private final BLSPublicKey publicKey = keyPair.getPublicKey();

  @Test
  void testGraffitiParserLoadsAFileSuccessfully() throws Exception {
    assertThat(GraffitiParser.loadFromFile(Path.of("src/test/resources/graffitiSample.txt")))
        .isEqualTo(Bytes32.rightPad(Bytes.wrap("123456789".getBytes(UTF_8))));
    assertThat(GraffitiParser.loadFromFile(Path.of("src/test/resources/32NullBytes.txt")))
        .isEqualTo(Bytes32.fromHexString("00".repeat(32)));
  }

  @Test
  void testGraffitiParserWithMoreThanMaximumAllowableBytesInTheFile() throws Exception {
    assertThatThrownBy(
            () ->
                GraffitiParser.loadFromFile(
                    Path.of("src/test/resources/graffitiSample41Bytes.txt")))
        .isInstanceOf(GraffitiLoaderException.class);
  }

  @Test
  void testGraffitiParserLoadsEmptyFileSuccessfully() throws Exception {
    assertThat(GraffitiParser.loadFromFile(Path.of("src/test/resources/emptyGraffitiSample.txt")))
        .isEqualTo(Bytes32.ZERO);
  }

  @Test
  void testGraffitiParserDoesTrim() throws Exception {
    assertThat(
            GraffitiParser.loadFromFile(Path.of("src/test/resources/graffitiSampleWithSpaces.txt")))
        .isEqualTo(Bytes32.rightPad(Bytes.wrap("T E K U".getBytes(UTF_8))));
  }

  @Test
  void testStrip() throws Exception {
    assertThat(GraffitiParser.strip(" \n  123  \n\n  ".getBytes(UTF_8))).isEqualTo("123");
  }

  @Test
  void loadGraffitiProvider_shouldCreateFileBackedProviderWhenNoFile() {
    final GraffitiProvider provider =
        GraffitiParser.loadGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.empty());

    assertThat(provider).isInstanceOf(FileBackedGraffitiProvider.class);
  }

  @Test
  void loadGraffitiProvider_shouldCreateMultimodeProviderForYamlFile(final @TempDir Path tempDir)
      throws IOException {
    // Создаем временный YAML файл
    final Path yamlFile = tempDir.resolve("graffiti.yaml");

    // Настраиваем конфигурацию
    MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();
    config.ordered = List.of("first message", "second message");

    // Записываем в файл
    new ObjectMapper(new YAMLFactory()).writeValue(yamlFile.toFile(), config);

    // Проверяем, что создается правильный тип провайдера
    final GraffitiProvider provider =
        GraffitiParser.loadGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.of(yamlFile));

    assertThat(provider).isInstanceOf(MultimodeGraffitiProvider.class);
  }

  @Test
  void loadGraffitiProvider_shouldCreateFileBackedProviderForTextFile(final @TempDir Path tempDir)
      throws IOException {
    // Создаем временный текстовый файл
    final Path textFile = tempDir.resolve("graffiti.txt");
    Files.writeString(textFile, "Simple graffiti text");

    // Проверяем, что создается правильный тип провайдера
    final GraffitiProvider provider =
        GraffitiParser.loadGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.of(textFile));

    assertThat(provider).isInstanceOf(FileBackedGraffitiProvider.class);
  }

  @Test
  void loadGraffitiProvider_shouldHandleInvalidYamlFile(final @TempDir Path tempDir)
      throws IOException {
    // Создаем временный файл с некорректным YAML
    final Path invalidFile = tempDir.resolve("invalid.yaml");
    Files.writeString(invalidFile, "invalid: yaml: format: - missing closing");

    // Проверяем, что создается FileBackedGraffitiProvider, а не падает с ошибкой
    final GraffitiProvider provider =
        GraffitiParser.loadGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.of(invalidFile));

    assertThat(provider).isInstanceOf(FileBackedGraffitiProvider.class);
  }
}
