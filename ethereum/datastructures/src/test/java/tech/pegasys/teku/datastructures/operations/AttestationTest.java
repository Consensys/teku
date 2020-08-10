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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

class AttestationTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Bitlist aggregationBitfield = dataStructureUtil.randomBitlist();
  private AttestationData data = dataStructureUtil.randomAttestationData();
  private BLSSignature aggregateSignature = dataStructureUtil.randomSignature();

  private Attestation attestation = new Attestation(aggregationBitfield, data, aggregateSignature);

  @Test
  public void shouldBeDependentOnTargetBlockAndBeaconBlockRoot() {
    final Bytes32 targetRoot = Bytes32.fromHexString("0x01");
    final Bytes32 beaconBlockRoot = Bytes32.fromHexString("0x02");

    final Attestation attestation =
        new Attestation(
            aggregationBitfield,
            new AttestationData(
                UInt64.valueOf(1),
                UInt64.ZERO,
                beaconBlockRoot,
                new Checkpoint(UInt64.ONE, Bytes32.ZERO),
                new Checkpoint(UInt64.valueOf(10), targetRoot)),
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
                UInt64.valueOf(1),
                UInt64.ZERO,
                root,
                new Checkpoint(UInt64.ONE, Bytes32.ZERO),
                new Checkpoint(UInt64.valueOf(10), root)),
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
    Attestation testAttestation =
        new Attestation(dataStructureUtil.randomBitlist(), data, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = dataStructureUtil.randomAttestationData();
    while (Objects.equals(otherData, data)) {
      otherData = dataStructureUtil.randomAttestationData();
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

  @Test
  void roundtripViaSsz() {
    Attestation attestation = dataStructureUtil.randomAttestation();
    Attestation newAttestation =
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(attestation), Attestation.class);
    assertEquals(attestation, newAttestation);
  }
}
