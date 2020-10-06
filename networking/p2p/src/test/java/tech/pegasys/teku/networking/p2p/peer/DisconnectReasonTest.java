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

package tech.pegasys.teku.networking.p2p.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DisconnectReasonTest {

  @ParameterizedTest(name = "reasonCode={0}")
  @MethodSource("fromReasonCodeArguments")
  public void fromReasonCode(
      final UInt64 reasonCode, final Optional<DisconnectReason> expectedReason) {
    final Optional<DisconnectReason> result = DisconnectReason.fromReasonCode(reasonCode);
    assertThat(result).isEqualTo(expectedReason);
  }

  public static Stream<Arguments> fromReasonCodeArguments() {
    return Stream.of(
        Arguments.of(
            GoodbyeMessage.REASON_CLIENT_SHUT_DOWN, Optional.of(DisconnectReason.SHUTTING_DOWN)),
        Arguments.of(
            GoodbyeMessage.REASON_IRRELEVANT_NETWORK,
            Optional.of(DisconnectReason.IRRELEVANT_NETWORK)),
        Arguments.of(GoodbyeMessage.REASON_FAULT_ERROR, Optional.of(DisconnectReason.REMOTE_FAULT)),
        Arguments.of(
            GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK,
            Optional.of(DisconnectReason.UNABLE_TO_VERIFY_NETWORK)),
        Arguments.of(
            GoodbyeMessage.REASON_TOO_MANY_PEERS, Optional.of(DisconnectReason.TOO_MANY_PEERS)),
        Arguments.of(
            GoodbyeMessage.REASON_RATE_LIMITING, Optional.of(DisconnectReason.RATE_LIMITING)),
        Arguments.of(UInt64.MAX_VALUE, Optional.empty()));
  }
}
