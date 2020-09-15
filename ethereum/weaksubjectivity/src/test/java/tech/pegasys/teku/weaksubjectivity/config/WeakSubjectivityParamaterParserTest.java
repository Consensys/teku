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

package tech.pegasys.teku.weaksubjectivity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WeakSubjectivityParamaterParserTest {
  private final WeakSubjectivityParameterParser parser = new WeakSubjectivityParameterParser();

  @Test
  public void parseCheckpoint_valid() {
    final Bytes32 blockRoot = Bytes32.fromHexStringLenient("0x0102");
    final String blockRootStr =
        "0x0000000000000000000000000000000000000000000000000000000000000102";
    final UInt64 epoch = UInt64.valueOf(11);
    final String epochStr = "11";
    final String input = blockRootStr + ":" + epochStr;

    Checkpoint result = parser.parseCheckpoint(input);
    assertThat(result.getRoot()).isEqualTo(blockRoot);
    assertThat(result.getEpoch()).isEqualTo(epoch);
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void parseCheckpoint_valid(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.parseCheckpoint(input);
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void parseCheckpoint_valid_withLeadingWhitespace(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.parseCheckpoint("  " + input);
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void parseCheckpoint_valid_withTrailingWhitespace(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.parseCheckpoint(input + " ");
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getValidCheckpointArguments")
  public void parseCheckpoint_valid_withLeadingAndTrailingWhitespace(
      final String input, final String blockRoot, final String epoch) {
    Checkpoint result = parser.parseCheckpoint(" " + input + "   ");
    assertThat(result.getRoot()).isEqualTo(Bytes32.fromHexString(blockRoot));
    assertThat(result.getEpoch()).isEqualTo(UInt64.valueOf(epoch));
  }

  @ParameterizedTest(name = "input = {0}")
  @MethodSource("getInvalidCheckpointArguments")
  public void parseCheckpoint_invalid(final String input) {
    assertThatThrownBy(() -> parser.parseCheckpoint(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(WeakSubjectivityParameterParser.CHECKPOINT_ERROR);
  }

  public static Stream<Arguments> getValidCheckpointArguments() {
    List<Arguments> blockRoots = validBlockRootValues().collect(Collectors.toList());
    List<Arguments> epochs = validEpochNumbers().collect(Collectors.toList());

    final Stream.Builder<Arguments> builder = Stream.builder();
    for (Arguments blockRoot : blockRoots) {
      for (Arguments epoch : epochs) {
        final String input = blockRoot.get()[0] + ":" + epoch.get()[0];
        builder.add(Arguments.of(input, blockRoot.get()[0], epoch.get()[0]));
      }
    }

    return builder.build();
  }

  public static Stream<Arguments> getInvalidCheckpointArguments() {
    List<String> validBlockRoots =
        validBlockRootValues().map(a -> (String) a.get()[0]).collect(Collectors.toList());
    List<String> validEpochs =
        validEpochNumbers().map(a -> (String) a.get()[0]).collect(Collectors.toList());
    List<String> invalidBlockRoots =
        invalidBlockRootValues().map(a -> (String) a.get()[0]).collect(Collectors.toList());
    List<String> invalidEpochs =
        invalidEpochNumbers().map(a -> (String) a.get()[0]).collect(Collectors.toList());
    final Stream.Builder<Arguments> builder = Stream.builder();

    // Use valid args in reverse order
    for (String blockRoot : validBlockRoots) {
      for (String epoch : validEpochs) {
        builder.add(Arguments.of(epoch + ":" + blockRoot));
      }
    }
    // Valid epoch and invalid block root
    for (String blockRoot : invalidBlockRoots) {
      for (String epoch : validEpochs) {
        builder.add(Arguments.of(blockRoot + ":" + epoch));
      }
    }
    // Valid block root and invalid epoch
    for (String blockRoot : validBlockRoots) {
      for (String epoch : invalidEpochs) {
        builder.add(Arguments.of(blockRoot + ":" + epoch));
      }
    }
    // Invalid block root and epoch
    for (String blockRoot : invalidBlockRoots) {
      for (String epoch : invalidEpochs) {
        builder.add(Arguments.of(blockRoot + ":" + epoch));
      }
    }

    return builder.build();
  }

  private static Stream<Arguments> validBlockRootValues() {
    return Stream.of(
        Arguments.of(Bytes32.fromHexStringLenient("0x0102").toUnprefixedHexString()),
        Arguments.of(Bytes32.fromHexStringLenient("0x0102").toHexString()));
  }

  private static Stream<Arguments> validEpochNumbers() {
    return Stream.of(Arguments.of("11"), Arguments.of("12345678"));
  }

  private static Stream<Arguments> invalidBlockRootValues() {
    return Stream.of(
        Arguments.of(
            Bytes32.fromHexStringLenient("0x0102").toUnprefixedHexString().substring(0, 30)),
        Arguments.of(Bytes32.fromHexStringLenient("0x0102").toHexString().substring(0, 30)),
        Arguments.of(""));
  }

  private static Stream<Arguments> invalidEpochNumbers() {
    return Stream.of(Arguments.of("0xFF00"), Arguments.of(""));
  }
}
