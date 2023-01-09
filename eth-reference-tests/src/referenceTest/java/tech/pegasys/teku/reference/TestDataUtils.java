/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.Snappy;
import org.yaml.snakeyaml.LoaderOptions;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class TestDataUtils {

  private static final YAMLFactory YAML_FACTORY;

  static {
    final LoaderOptions loaderOptions = new LoaderOptions();
    // Set the code point limit to 100MB - context:
    // https://github.com/FasterXML/jackson-dataformats-text/tree/2.15/yaml#maximum-input-yaml-document-size-3-mb
    loaderOptions.setCodePointLimit(1024 * 1024 * 100);
    YAML_FACTORY = YAMLFactory.builder().loaderOptions(loaderOptions).build();
  }

  public static <T extends SszData> T loadSsz(
      final TestDefinition testDefinition, final String fileName, final SszSchema<T> type) {
    return loadSsz(testDefinition, fileName, type::sszDeserialize);
  }

  public static <T> T loadSsz(
      final TestDefinition testDefinition,
      final String fileName,
      final Function<Bytes, T> deserializer) {
    try {
      final Bytes sszData = readSszData(testDefinition, fileName);
      return deserializer.apply(sszData);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Bytes readSszData(final TestDefinition testDefinition, final String fileName)
      throws IOException {
    final Path testDirectory = testDefinition.getTestDirectory();
    final Path path = testDirectory.resolve(fileName);
    final byte[] fileContent = Files.readAllBytes(path);
    if (fileName.endsWith("_snappy")) {
      return Bytes.wrap(Snappy.uncompress(fileContent));
    } else {
      return Bytes.wrap(fileContent);
    }
  }

  public static BeaconState loadStateFromSsz(
      final TestDefinition testDefinition, final String fileName) {
    return loadSsz(
        testDefinition,
        fileName,
        testDefinition.getSpec().getGenesisSchemaDefinitions().getBeaconStateSchema());
  }

  public static <T> T loadYaml(
      final TestDefinition testDefinition, final String fileName, final Class<T> type)
      throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    try (final InputStream in = Files.newInputStream(path)) {
      return new ObjectMapper(YAML_FACTORY).readerFor(type).readValue(in);
    }
  }

  public static <T> T loadYaml(
      final TestDefinition testDefinition,
      final String fileName,
      final DeserializableTypeDefinition<T> type)
      throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    try (final YAMLParser in = YAML_FACTORY.createParser(Files.newInputStream(path))) {
      in.nextToken();
      return type.deserialize(in);
    }
  }
}
