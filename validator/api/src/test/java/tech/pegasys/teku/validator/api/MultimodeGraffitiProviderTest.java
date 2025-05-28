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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MultimodeGraffitiProviderTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final Bytes32 defaultGraffiti = Bytes32Parser.toBytes32("default graffiti");
  private final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
  private final BLSPublicKey publicKey = keyPair.getPublicKey();
  private final String pubKeyHex = publicKey.toSSZBytes().toUnprefixedHexString();
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  @TempDir Path tempDir;
  private Path graffitiFile;

  @BeforeEach
  void setup() {
    graffitiFile = tempDir.resolve("graffiti.yaml");
  }

  @Test
  void shouldUseDefaultGraffitiWhenFileIsEmpty() throws IOException {
    Files.writeString(graffitiFile, "");

    final MultimodeGraffitiProvider provider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.of(graffitiFile));

    assertThat(provider.get()).isEqualTo(Optional.of(defaultGraffiti));
  }

  @Test
  void shouldUseSpecificGraffitiForValidator() throws IOException {
    // Setup config with specific validator entry
    final MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();
    final Map<String, String> specific = new HashMap<>();
    specific.put(pubKeyHex, "specific validator graffiti");
    config.specific = specific;
    config.defaultGraffiti = "file default graffiti";

    // Write config to file
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Create provider with the validator public key
    final MultimodeGraffitiProvider provider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.of(graffitiFile));

    // The provider should use the specific graffiti for this validator
    assertThat(provider.get())
        .isEqualTo(Optional.of(Bytes32Parser.toBytes32("specific validator graffiti")));
  }

  @Test
  void shouldUseOrderedGraffitiInSequence() throws IOException {
    // Setup config with ordered list
    final MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();
    config.ordered = List.of("first message", "second message", "third message");
    config.defaultGraffiti = "file default graffiti";

    // Write config to file
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Create provider
    final MultimodeGraffitiProvider provider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.empty(), Optional.of(graffitiFile));

    // The provider should return messages in order
    assertThat(provider.get()).isEqualTo(Optional.of(Bytes32Parser.toBytes32("first message")));
    assertThat(provider.get()).isEqualTo(Optional.of(Bytes32Parser.toBytes32("second message")));
    assertThat(provider.get()).isEqualTo(Optional.of(Bytes32Parser.toBytes32("third message")));
    // Should cycle back to the first message
    assertThat(provider.get()).isEqualTo(Optional.of(Bytes32Parser.toBytes32("first message")));
  }

  @Test
  void shouldFallbackToRandomIfOrderedIsEmpty() throws IOException {
    // Setup config with random list and empty ordered list
    final MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();
    config.ordered = List.of();
    config.random = List.of("random1", "random2", "random3", "random4", "random5");
    config.defaultGraffiti = "file default graffiti";

    // Write config to file
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Create provider
    final MultimodeGraffitiProvider provider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.empty(), Optional.of(graffitiFile));

    // Get multiple values to test randomness
    final Bytes32 value1 = provider.get().orElseThrow();
    final Bytes32 value2 = provider.get().orElseThrow();
    final Bytes32 value3 = provider.get().orElseThrow();
    final Bytes32 value4 = provider.get().orElseThrow();
    final Bytes32 value5 = provider.get().orElseThrow();

    // Ensure all values are from the random list
    final List<Bytes32> possibleValues =
        List.of(
            Bytes32Parser.toBytes32("random1"),
            Bytes32Parser.toBytes32("random2"),
            Bytes32Parser.toBytes32("random3"),
            Bytes32Parser.toBytes32("random4"),
            Bytes32Parser.toBytes32("random5"));

    assertThat(possibleValues).contains(value1, value2, value3, value4, value5);

    // With 5 selections from 5 values, it's extremely likely we'll get at least one duplicate,
    // but it's statistically possible they could all be different, so we can't assert on that.
  }

  @Test
  void shouldUseFileDefaultWhenNoOtherOptionApplies() throws IOException {
    // Setup config with only default
    final MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();
    config.defaultGraffiti = "file default graffiti";

    // Write config to file
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Create provider without a public key
    final MultimodeGraffitiProvider provider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.empty(), Optional.of(graffitiFile));

    // Should use the default from file
    assertThat(provider.get())
        .isEqualTo(Optional.of(Bytes32Parser.toBytes32("file default graffiti")));
  }

  @Test
  void shouldUseCLIDefaultWhenNoFileOptionAvailable() throws IOException {
    // Setup minimal config with no default
    final MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();

    // Write config to file
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Create provider without a public key
    final MultimodeGraffitiProvider provider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.empty(), Optional.of(graffitiFile));

    // Should use the default from CLI
    assertThat(provider.get()).isEqualTo(Optional.of(defaultGraffiti));
  }

  @Test
  void shouldRespectPriorityOrder() throws IOException {
    // Setup config with all options
    final MultimodeGraffitiProvider.GraffitiConfiguration config =
        new MultimodeGraffitiProvider.GraffitiConfiguration();

    // Add specific validator entry
    final Map<String, String> specific = new HashMap<>();
    specific.put(pubKeyHex, "specific validator graffiti");
    config.specific = specific;

    // Add ordered entries
    config.ordered = List.of("ordered1", "ordered2", "ordered3");

    // Add random entries
    config.random = List.of("random1", "random2", "random3");

    // Add default
    config.defaultGraffiti = "file default graffiti";

    // Write config to file
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Test with validator public key to get specific entry (highest priority)
    final MultimodeGraffitiProvider specificProvider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.of(publicKey), Optional.of(graffitiFile));
    assertThat(specificProvider.get())
        .isEqualTo(Optional.of(Bytes32Parser.toBytes32("specific validator graffiti")));

    // Test without a specific validator to get ordered entry (next priority)
    final MultimodeGraffitiProvider otherProvider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.empty(), Optional.of(graffitiFile));
    assertThat(otherProvider.get()).isEqualTo(Optional.of(Bytes32Parser.toBytes32("ordered1")));

    // If we remove ordered, we should get random
    config.ordered = null;
    yamlMapper.writeValue(graffitiFile.toFile(), config);

    // Create new provider (to reload config) and validate we get a random entry
    final MultimodeGraffitiProvider randomProvider =
        new MultimodeGraffitiProvider(
            Optional.of(defaultGraffiti), Optional.empty(), Optional.of(graffitiFile));
    final Bytes32 randomValue = randomProvider.get().orElseThrow();
    final List<Bytes32> possibleRandomValues =
        List.of(
            Bytes32Parser.toBytes32("random1"),
            Bytes32Parser.toBytes32("random2"),
            Bytes32Parser.toBytes32("random3"));
    assertThat(possibleRandomValues).contains(randomValue);
  }
}
