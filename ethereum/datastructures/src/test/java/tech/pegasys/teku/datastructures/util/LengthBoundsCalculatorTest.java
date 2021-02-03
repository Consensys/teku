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
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.ssz.backing.type.ViewType;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
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
  void shouldCalculateCorrectLengthBounds(final ViewType<?> type, final SszLengthBounds expected) {
    assertThat(type.getSszLengthBounds()).isEqualTo(expected);
  }

  // Expected values taken from https://gist.github.com/protolambda/db75c7faa1e94f2464787a480e5d613e
  static Stream<Arguments> generateParameters() {
    return Stream.of(
        Arguments.of(AggregateAndProof.TYPE, SszLengthBounds.ofBytes(337, 593)),
        Arguments.of(Attestation.TYPE, SszLengthBounds.ofBytes(229, 485)),
        Arguments.of(AttestationData.TYPE, SszLengthBounds.ofBytes(128, 128)),
        Arguments.of(AttesterSlashing.TYPE, SszLengthBounds.ofBytes(464, 33232)),
        Arguments.of(BeaconBlock.TYPE.get(), SszLengthBounds.ofBytes(304, 157656)),
        Arguments.of(BeaconBlockBody.TYPE.get(), SszLengthBounds.ofBytes(220, 157572)),
        Arguments.of(BeaconBlockHeader.TYPE, SszLengthBounds.ofBytes(112, 112)),
        Arguments.of(BeaconState.TYPE.get(), SszLengthBounds.ofBytes(2687377, 141837543039377L)),
        Arguments.of(Checkpoint.TYPE, SszLengthBounds.ofBytes(40, 40)),
        Arguments.of(Deposit.TYPE, SszLengthBounds.ofBytes(1240, 1240)),
        Arguments.of(DepositData.TYPE, SszLengthBounds.ofBytes(184, 184)),
        Arguments.of(DepositMessage.TYPE, SszLengthBounds.ofBytes(88, 88)),
        Arguments.of(Eth1Data.TYPE, SszLengthBounds.ofBytes(72, 72)),
        Arguments.of(Fork.TYPE, SszLengthBounds.ofBytes(16, 16)),
        Arguments.of(ForkData.TYPE, SszLengthBounds.ofBytes(36, 36)),
        Arguments.of(HistoricalBatch.TYPE.get(), SszLengthBounds.ofBytes(524288, 524288)),
        Arguments.of(IndexedAttestation.TYPE, SszLengthBounds.ofBytes(228, 16612)),
        Arguments.of(PendingAttestation.TYPE, SszLengthBounds.ofBytes(149, 405)),
        Arguments.of(ProposerSlashing.TYPE, SszLengthBounds.ofBytes(416, 416)),
        Arguments.of(SignedAggregateAndProof.TYPE, SszLengthBounds.ofBytes(437, 693)),
        Arguments.of(SignedBeaconBlock.TYPE.get(), SszLengthBounds.ofBytes(404, 157756)),
        Arguments.of(SignedBeaconBlockHeader.TYPE, SszLengthBounds.ofBytes(208, 208)),
        Arguments.of(SignedVoluntaryExit.TYPE, SszLengthBounds.ofBytes(112, 112)),
        Arguments.of(Validator.TYPE, SszLengthBounds.ofBytes(121, 121)),
        Arguments.of(VoluntaryExit.TYPE, SszLengthBounds.ofBytes(16, 16)),
        Arguments.of(MetadataMessage.TYPE, SszLengthBounds.ofBytes(16, 16)),
        Arguments.of(StatusMessage.TYPE, SszLengthBounds.ofBytes(84, 84)),
        Arguments.of(GoodbyeMessage.TYPE, SszLengthBounds.ofBytes(8, 8)),
        Arguments.of(BeaconBlocksByRangeRequestMessage.TYPE, SszLengthBounds.ofBytes(24, 24)));
  }
}
