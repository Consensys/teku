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

package tech.pegasys.teku.reference.phase0;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TestDataUtils {

  public static <T> T loadSsz(
      final TestDefinition testDefinition, final String fileName, final Class<T> type) {
    try {
      final Path path = testDefinition.getTestDirectory().resolve(fileName);
      return SimpleOffsetSerializer.deserialize(Bytes.wrap(Files.readAllBytes(path)), type);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static BeaconState loadStateFromSsz(
      final TestDefinition testDefinition, final String fileName) {
    return loadSsz(testDefinition, fileName, BeaconStateImpl.class);
  }

  public static Bytes32 loadBytes32FromSsz(
      final TestDefinition testDefinition, final String fileName) throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    return Bytes32.wrap(Files.readAllBytes(path));
  }

  public static UInt64 loadUInt64FromYaml(
      final TestDefinition testDefinition, final String fileName) throws IOException {
    return UInt64.fromLongBits(loadYaml(testDefinition, fileName, Long.class));
  }

  public static <T> T loadYaml(
      final TestDefinition testDefinition, final String fileName, final Class<T> type)
      throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    try (final InputStream in = Files.newInputStream(path)) {
      return new ObjectMapper(new YAMLFactory()).readerFor(type).readValue(in);
    }
  }
}
