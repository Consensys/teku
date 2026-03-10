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

package tech.pegasys.teku.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ConverterTest {

  @ParameterizedTest
  @MethodSource("getWeiToEthArguments")
  void testWeiToEth(final UInt256 wei, final String expected) {
    String output = Converter.weiToEth(wei);
    assertThat(output).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("getWeiToGweiArguments")
  void testWeiToGwei(final UInt256 wei, final UInt64 expected) {
    UInt64 output = Converter.weiToGwei(wei);
    assertThat(output).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("getGweiToEthArguments")
  void testGweiToEth(final UInt64 gwei, final String expected) {
    String output = Converter.gweiToEth(gwei);
    assertThat(output).isEqualTo(expected);
  }

  private static Stream<Arguments> getWeiToEthArguments() {
    return Stream.of(
        Arguments.of(UInt256.valueOf(1), "0.000000"),
        Arguments.of(UInt256.valueOf(1000), "0.000000"),
        Arguments.of(UInt256.valueOf(3401220000000000L), "0.003401"),
        Arguments.of(UInt256.valueOf(889999203452340000L), "0.889999"));
  }

  private static Stream<Arguments> getWeiToGweiArguments() {
    return Stream.of(
        Arguments.of(UInt256.valueOf(1), UInt64.valueOf(0)),
        Arguments.of(UInt256.valueOf(1000), UInt64.valueOf(0)),
        Arguments.of(UInt256.valueOf(3401220000000000L), UInt64.valueOf(3401220)),
        Arguments.of(UInt256.valueOf(889999203452340000L), UInt64.valueOf(889999203)),
        Arguments.of(UInt256.valueOf(424242424242424242L), UInt64.valueOf(424242424)));
  }

  private static Stream<Arguments> getGweiToEthArguments() {
    return Stream.of(Arguments.of(UInt64.valueOf(424242424), "0.424242"));
  }
}
