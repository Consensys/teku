/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.versions.altair.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class AttestationWorthinessCheckerAltairTest {

  final Spec spec = TestSpecFactory.createMainnetAltair();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final AttestationData referenceAttestationData =
      dataStructureUtil.randomAttestationData(UInt64.valueOf(1));
  final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(10));
  final Checkpoint correctTarget =
      new Checkpoint(
          dataStructureUtil.randomEpoch(),
          spec.atSlot(state.getSlot()).beaconStateAccessors().getBlockRootAtSlot(state, ZERO));
  final Checkpoint wrongTarget = dataStructureUtil.randomCheckpoint();

  @Test
  void shouldIncludeSlotOldTargetOK() {
    AttestationData attestation = generateAttestationData(UInt64.valueOf(1), correctTarget);

    assertThat(spec.createAttestationWorthinessChecker(state).areAttestationsWorthy(attestation))
        .isTrue();
  }

  @Test
  void shouldIncludeSlotOldTargetWrong() {
    AttestationData attestation = generateAttestationData(UInt64.valueOf(3), wrongTarget);

    assertThat(spec.createAttestationWorthinessChecker(state).areAttestationsWorthy(attestation))
        .isFalse();
  }

  @Test
  void shouldIncludeSlotOKTargetWrong() {
    AttestationData attestation = generateAttestationData(UInt64.valueOf(5), wrongTarget);

    assertThat(spec.createAttestationWorthinessChecker(state).areAttestationsWorthy(attestation))
        .isTrue();
  }

  @Test
  void shouldIncludeSlotOKTargetOK() {
    AttestationData attestation = generateAttestationData(UInt64.valueOf(7), correctTarget);

    assertThat(spec.createAttestationWorthinessChecker(state).areAttestationsWorthy(attestation))
        .isTrue();
  }

  @Test
  void shouldIncludeCloseToGenesis() {
    final BeaconState closeToGenesisState = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));

    AttestationData attestation = generateAttestationData(UInt64.valueOf(1), wrongTarget);

    assertThat(
            spec.createAttestationWorthinessChecker(closeToGenesisState)
                .areAttestationsWorthy(attestation))
        .isTrue();
  }

  private AttestationData generateAttestationData(UInt64 slot, Checkpoint target) {
    return new AttestationData(
        slot,
        referenceAttestationData.getIndex(),
        referenceAttestationData.getBeacon_block_root(),
        referenceAttestationData.getSource(),
        target);
  }
}
