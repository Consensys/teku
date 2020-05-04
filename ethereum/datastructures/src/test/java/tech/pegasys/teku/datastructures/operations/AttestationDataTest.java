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

package tech.pegasys.teku.datastructures.operations;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.MIN_ATTESTATION_INCLUSION_DELAY;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.util.config.Constants;

class AttestationDataTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private UnsignedLong slot = dataStructureUtil.randomUnsignedLong();
  private UnsignedLong index = dataStructureUtil.randomUnsignedLong();
  private Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
  private UnsignedLong source_epoch = dataStructureUtil.randomUnsignedLong();
  private Bytes32 source_root = dataStructureUtil.randomBytes32();
  private UnsignedLong target_epoch = dataStructureUtil.randomUnsignedLong();
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
            UnsignedLong.valueOf(60),
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO));

    assertThat(data.getEarliestSlotForForkChoice()).isEqualTo(UnsignedLong.valueOf(61));
  }

  @Test
  void shouldNotBeProcessableBeforeFirstSlotOfTargetEpoch() {
    final Checkpoint target = new Checkpoint(UnsignedLong.valueOf(10), Bytes32.ZERO);
    final AttestationData data =
        new AttestationData(
            UnsignedLong.valueOf(1),
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            target);

    assertThat(data.getEarliestSlotForForkChoice()).isEqualTo(target.getEpochStartSlot());
  }

  @Test
  public void shouldNotBeAbleToIncludeInBlockWithinMinAttestationInclusionDelay() {
    final UnsignedLong attestationSlot = UnsignedLong.valueOf(60);
    final AttestationData data =
        new AttestationData(
            attestationSlot,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO));

    assertThat(data.canIncludeInBlockAtSlot(attestationSlot)).isFalse();
    assertThat(data.canIncludeInBlockAtSlot(attestationSlot.plus(ONE))).isFalse();
    assertThat(data.canIncludeInBlockAtSlot(attestationSlot.plus(UnsignedLong.valueOf(2))))
        .isTrue();
  }

  @Test
  public void shouldNotBeAbleToIncludeInBlockBeforeFirstSlotOfTargetEpoch() {
    final Checkpoint target = new Checkpoint(UnsignedLong.valueOf(10), Bytes32.ZERO);
    final AttestationData data =
        new AttestationData(
            target.getEpochStartSlot().minus(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY)),
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            target);

    assertThat(data.canIncludeInBlockAtSlot(target.getEpochStartSlot().minus(ONE))).isFalse();
    assertThat(data.canIncludeInBlockAtSlot(target.getEpochStartSlot())).isTrue();
  }

  @Test
  public void shouldNotBeAbleToIncludeInBlockTooFarAfterAttestationSlot() {
    final AttestationData data =
        new AttestationData(
            UnsignedLong.valueOf(60),
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO));

    final UnsignedLong lastValidSlot = data.getSlot().plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    assertThat(data.canIncludeInBlockAtSlot(lastValidSlot)).isTrue();
    assertThat(data.canIncludeInBlockAtSlot(lastValidSlot.plus(ONE))).isFalse();
  }

  @Test
  public void shouldNotBeAbleToIncludeInBlockMoreThanOneEpochLater() {
    final UnsignedLong attestationEpoch = UnsignedLong.valueOf(6);
    final UnsignedLong nextEpoch = attestationEpoch.plus(ONE);
    final UnsignedLong tooLateEpoch = nextEpoch.plus(ONE);
    final AttestationData data =
        new AttestationData(
            compute_start_slot_at_epoch(attestationEpoch).plus(UnsignedLong.valueOf(3)),
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO));

    // In the attestation epoch, but has to be after the attestation slot
    final UnsignedLong slotInCurrentEpoch =
        data.getSlot().plus(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY));
    assertThat(compute_epoch_at_slot(slotInCurrentEpoch)).isEqualTo(attestationEpoch);
    assertThat(data.canIncludeInBlockAtSlot(slotInCurrentEpoch)).isTrue();

    // Next epoch is ok
    assertThat(data.canIncludeInBlockAtSlot(compute_start_slot_at_epoch(nextEpoch))).isTrue();

    // Very last slot of next epoch is more than SLOTS_PER_EPOCH later so is not ok
    assertThat(data.canIncludeInBlockAtSlot(compute_start_slot_at_epoch(tooLateEpoch).minus(ONE)))
        .isFalse();

    // Epoch after that is not ok.
    assertThat(data.canIncludeInBlockAtSlot(compute_start_slot_at_epoch(tooLateEpoch))).isFalse();
  }

  @Test
  public void shouldNotBeAbleToIncludeInBlockBeforeAttestationSlot() {
    final UnsignedLong attestationSlot = UnsignedLong.valueOf(13);
    final UnsignedLong blockSlot = UnsignedLong.valueOf(14);
    final AttestationData data =
        new AttestationData(
            attestationSlot,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            new Checkpoint(ONE, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO));

    assertThat(data.canIncludeInBlockAtSlot(blockSlot)).isFalse();
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
    Checkpoint newSource = new Checkpoint(dataStructureUtil.randomUnsignedLong(), source.getRoot());
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
    Checkpoint newTarget = new Checkpoint(dataStructureUtil.randomUnsignedLong(), target.getRoot());
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
        new AttestationData(UnsignedLong.valueOf(1234), index, beaconBlockRoot, source, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenIndexIsDifferent() {
    AttestationData testAttestationData =
        new AttestationData(slot, UnsignedLong.valueOf(1234), beaconBlockRoot, source, target);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataBytes = SimpleOffsetSerializer.serialize(attestationData);
    assertEquals(
        attestationData,
        SimpleOffsetSerializer.deserialize(sszAttestationDataBytes, AttestationData.class));
  }
}
