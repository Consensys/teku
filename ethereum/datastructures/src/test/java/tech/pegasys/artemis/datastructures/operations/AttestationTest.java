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

package tech.pegasys.artemis.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttestationData;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBitlist;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSSignature;

class AttestationTest {
  private int seed = 100;
  private Bitlist aggregationBitfield = randomBitlist(seed);
  private AttestationData data = randomAttestationData(seed++);
  private BLSSignature aggregateSignature = BLSSignature.random(seed++);

  private Attestation attestation = new Attestation(aggregationBitfield, data, aggregateSignature);

  @Test
  void shouldNotBeProcessableBeforeSlotAfterCreationSlot() {
    final Attestation attestation =
        new Attestation(
            aggregationBitfield,
            new AttestationData(
                UnsignedLong.valueOf(60),
                UnsignedLong.ZERO,
                Bytes32.ZERO,
                new Checkpoint(UnsignedLong.ONE, Bytes32.ZERO),
                new Checkpoint(UnsignedLong.ONE, Bytes32.ZERO)),
            BLSSignature.empty());

    assertThat(attestation.getEarliestSlotForProcessing()).isEqualTo(UnsignedLong.valueOf(61));
  }

  @Test
  void shouldNotBeProcessableBeforeFirstSlotOfTargetEpoch() {
    final Checkpoint target = new Checkpoint(UnsignedLong.valueOf(10), Bytes32.ZERO);
    final Attestation attestation =
        new Attestation(
            aggregationBitfield,
            new AttestationData(
                UnsignedLong.valueOf(1),
                UnsignedLong.ZERO,
                Bytes32.ZERO,
                new Checkpoint(UnsignedLong.ONE, Bytes32.ZERO),
                target),
            BLSSignature.empty());

    assertThat(attestation.getEarliestSlotForProcessing()).isEqualTo(target.getEpochStartSlot());
  }

  @Test
  public void shouldBeDependentOnTargetBlockAndBeaconBlockRoot() {
    final Bytes32 targetRoot = Bytes32.fromHexString("0x01");
    final Bytes32 beaconBlockRoot = Bytes32.fromHexString("0x02");

    final Attestation attestation =
        new Attestation(
            aggregationBitfield,
            new AttestationData(
                UnsignedLong.valueOf(1),
                UnsignedLong.ZERO,
                beaconBlockRoot,
                new Checkpoint(UnsignedLong.ONE, Bytes32.ZERO),
                new Checkpoint(UnsignedLong.valueOf(10), targetRoot)),
            BLSSignature.empty());

    assertThat(attestation.getDependentBlockRoots())
        .containsExactlyInAnyOrder(targetRoot, beaconBlockRoot);
  }

  @Test
  public void shouldBeDependentOnSingleBlockWhenTargetBlockAndBeaconBlockRootAreEqual() {
    final Bytes32 root = Bytes32.fromHexString("0x01");

    final Attestation attestation =
        new Attestation(
            aggregationBitfield,
            new AttestationData(
                UnsignedLong.valueOf(1),
                UnsignedLong.ZERO,
                root,
                new Checkpoint(UnsignedLong.ONE, Bytes32.ZERO),
                new Checkpoint(UnsignedLong.valueOf(10), root)),
            BLSSignature.empty());

    assertThat(attestation.getDependentBlockRoots()).containsExactlyInAnyOrder(root);
  }

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    Attestation testAttestation = attestation;

    assertEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Attestation testAttestation = new Attestation(aggregationBitfield, data, aggregateSignature);

    assertEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregationBitfieldsAreDifferent() {
    Attestation testAttestation = new Attestation(randomBitlist(seed++), data, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData(seed++);
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData(seed++);
    }

    Attestation testAttestation =
        new Attestation(aggregationBitfield, otherData, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    BLSSignature differentAggregateSignature = BLSSignature.random(99);
    Attestation testAttestation =
        new Attestation(aggregationBitfield, data, differentAggregateSignature);

    assertNotEquals(aggregateSignature, differentAggregateSignature);
    assertNotEquals(attestation, testAttestation);
  }
}
