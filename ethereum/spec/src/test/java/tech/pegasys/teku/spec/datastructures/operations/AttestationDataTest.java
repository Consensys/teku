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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.util.config.Constants;

class AttestationDataTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private UInt64 slot = dataStructureUtil.randomUInt64();
  private UInt64 index = dataStructureUtil.randomUInt64();
  private Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
  private UInt64 source_epoch = dataStructureUtil.randomUInt64();
  private Bytes32 source_root = dataStructureUtil.randomBytes32();
  private UInt64 target_epoch = dataStructureUtil.randomUInt64();
  private Bytes32 target_root = dataStructureUtil.randomBytes32();
  private Checkpoint source = new Checkpoint(source_epoch, source_root);
  private Checkpoint target = new Checkpoint(target_epoch, target_root);

  private AttestationData attestationData =
      new AttestationData(slot, index, beaconBlockRoot, source, target);

  @BeforeAll
  static void initialize() {
    Constants.MIN_ATTESTATION_INCLUSION_DELAY = 2;
  }

  @AfterAll
  static void reset() {
    Constants.setConstants("minimal");
  }

  @Test
  void shouldNotBeProcessableBeforeSlotAfterCreationSlot() {
    final AttestationData data =
        new AttestationData(
            UInt64.valueOf(60),
            UInt64.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO));

    assertThat(data.getEarliestSlotForForkChoice(spec)).isEqualTo(UInt64.valueOf(61));
  }

  @Test
  void shouldNotBeProcessableBeforeFirstSlotOfTargetEpoch() {
    final Checkpoint target = new Checkpoint(UInt64.valueOf(10), Bytes32.ZERO);
    final AttestationData data =
        new AttestationData(
            UInt64.valueOf(1),
            UInt64.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            target);

    assertThat(data.getEarliestSlotForForkChoice(spec)).isEqualTo(target.getEpochStartSlot(spec));
  }

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttestationData testAttestationData = attestationData;

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttestationData testAttestationData =
        new AttestationData(slot, index, beaconBlockRoot, source, target);

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(slot, index, Bytes32.random(), source, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSourceEpochsAreDifferent() {
    Checkpoint newSource = new Checkpoint(dataStructureUtil.randomUInt64(), source.getRoot());
    AttestationData testAttestationData =
        new AttestationData(slot, index, beaconBlockRoot, newSource, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSourceRootsAreDifferent() {
    Checkpoint newSource = new Checkpoint(source.getEpoch(), Bytes32.random());
    AttestationData testAttestationData =
        new AttestationData(slot, index, beaconBlockRoot, newSource, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenTargetEpochsAreDifferent() {
    Checkpoint newTarget = new Checkpoint(dataStructureUtil.randomUInt64(), target.getRoot());
    AttestationData testAttestationData =
        new AttestationData(slot, index, beaconBlockRoot, source, newTarget);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenTargetRootsAreDifferent() {
    Checkpoint newTarget = new Checkpoint(target.getEpoch(), Bytes32.random());
    AttestationData testAttestationData =
        new AttestationData(slot, index, beaconBlockRoot, source, newTarget);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSlotIsDifferent() {
    AttestationData testAttestationData =
        new AttestationData(UInt64.valueOf(1234), index, beaconBlockRoot, source, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenIndexIsDifferent() {
    AttestationData testAttestationData =
        new AttestationData(slot, UInt64.valueOf(1234), beaconBlockRoot, source, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataBytes = attestationData.sszSerialize();
    assertEquals(
        attestationData, AttestationData.SSZ_SCHEMA.sszDeserialize(sszAttestationDataBytes));
  }
}
