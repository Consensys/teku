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

package tech.pegasys.teku.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.util.config.Constants;

/**
 * This is in the wrong module but we want to be able to access the datastructure types to check
 * they produce the right value which is a much more valuable test than using fake classes
 */
public class LengthBoundsCalculatorTest {

  @BeforeAll
  static void setConstants() {
    Constants.setConstants("mainnet");
    SimpleOffsetSerializer.setConstants();
  }

  @AfterAll
  static void restoreConstants() {
    Constants.setConstants("minimal");
    SimpleOffsetSerializer.setConstants();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generateParameters")
  void shouldCalculateCorrectLengthBounds(final Class<?> type, final LengthBounds expected) {
    final LengthBounds actual = SimpleOffsetSerializer.getLengthBounds(type).orElseThrow();
    assertThat(actual).isEqualTo(expected);
  }

  // Expected values taken from https://gist.github.com/protolambda/db75c7faa1e94f2464787a480e5d613e
  static Stream<Arguments> generateParameters() {
    return Stream.of(
        Arguments.of(AggregateAndProof.class, new LengthBounds(337, 593)),
        Arguments.of(Attestation.class, new LengthBounds(229, 485)),
        Arguments.of(AttestationData.class, new LengthBounds(128, 128)),
        Arguments.of(AttesterSlashing.class, new LengthBounds(464, 33232)),
        Arguments.of(BeaconBlock.class, new LengthBounds(304, 157656)),
        Arguments.of(BeaconBlockBody.class, new LengthBounds(220, 157572)),
        Arguments.of(BeaconBlockHeader.class, new LengthBounds(112, 112)),
        Arguments.of(BeaconStateImpl.class, new LengthBounds(2687377, 141837543039377L)),
        Arguments.of(Checkpoint.class, new LengthBounds(40, 40)),
        Arguments.of(Deposit.class, new LengthBounds(1240, 1240)),
        Arguments.of(DepositData.class, new LengthBounds(184, 184)),
        Arguments.of(DepositMessage.class, new LengthBounds(88, 88)),
        Arguments.of(Eth1Data.class, new LengthBounds(72, 72)),
        Arguments.of(Fork.class, new LengthBounds(16, 16)),
        Arguments.of(ForkData.class, new LengthBounds(36, 36)),
        Arguments.of(HistoricalBatch.class, new LengthBounds(524288, 524288)),
        Arguments.of(IndexedAttestation.class, new LengthBounds(228, 16612)),
        Arguments.of(PendingAttestation.class, new LengthBounds(149, 405)),
        Arguments.of(ProposerSlashing.class, new LengthBounds(416, 416)),
        Arguments.of(SignedAggregateAndProof.class, new LengthBounds(437, 693)),
        Arguments.of(SignedBeaconBlock.class, new LengthBounds(404, 157756)),
        Arguments.of(SignedBeaconBlockHeader.class, new LengthBounds(208, 208)),
        Arguments.of(SignedVoluntaryExit.class, new LengthBounds(112, 112)),
        Arguments.of(Validator.class, new LengthBounds(121, 121)),
        Arguments.of(VoluntaryExit.class, new LengthBounds(16, 16)),
        Arguments.of(MetadataMessage.class, new LengthBounds(16, 16)),
        Arguments.of(StatusMessage.class, new LengthBounds(84, 84)),
        Arguments.of(GoodbyeMessage.class, new LengthBounds(8, 8)),
        Arguments.of(BeaconBlocksByRangeRequestMessage.class, new LengthBounds(24, 24)));
  }
}
