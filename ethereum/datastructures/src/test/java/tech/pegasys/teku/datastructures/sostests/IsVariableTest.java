/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.sostests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class IsVariableTest {

  static Stream<Arguments> variableSizeTypes() {
    return Stream.of(
        Arguments.of(BeaconBlockBody.TYPE.get()),
        Arguments.of(BeaconBlock.TYPE.get()),
        Arguments.of(Attestation.TYPE),
        Arguments.of(AttesterSlashing.TYPE),
        Arguments.of(IndexedAttestation.TYPE),
        Arguments.of(BeaconState.getSSZType()),
        Arguments.of(PendingAttestation.TYPE),
        Arguments.of(AggregateAndProof.TYPE));
  }

  static Stream<Arguments> fixedSizeTypes() {
    return Stream.of(
        Arguments.of(BeaconBlockHeader.TYPE),
        Arguments.of(Eth1Data.TYPE),
        Arguments.of(AttestationData.TYPE),
        Arguments.of(DepositData.TYPE),
        Arguments.of(Deposit.TYPE),
        Arguments.of(ProposerSlashing.TYPE),
        Arguments.of(VoluntaryExit.TYPE),
        Arguments.of(Checkpoint.TYPE),
        Arguments.of(Fork.TYPE),
        Arguments.of(HistoricalBatch.TYPE.get()),
        Arguments.of(VoteTracker.TYPE),
        Arguments.of(Validator.TYPE));
  }

  @ParameterizedTest
  @MethodSource("variableSizeTypes")
  void testTheTypeIsVariableSize(ViewType<?> type) {
    assertFalse(type.isFixedSize());
  }

  @ParameterizedTest
  @MethodSource("fixedSizeTypes")
  void testTheTypeIsFixedSize(ViewType<?> type) {
    assertTrue(type.isFixedSize());
  }
}
