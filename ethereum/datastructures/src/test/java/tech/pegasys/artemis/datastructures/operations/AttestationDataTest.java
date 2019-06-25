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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomCrosslink;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Crosslink;

class AttestationDataTest {

  private Bytes32 beaconBlockRoot = Bytes32.random();
  private UnsignedLong source_epoch = randomUnsignedLong();
  private Bytes32 source_root = Bytes32.random();
  private UnsignedLong target_epoch = randomUnsignedLong();
  private Bytes32 target_root = Bytes32.random();
  private Crosslink crosslink = randomCrosslink();

  private AttestationData attestationData =
      new AttestationData(
          beaconBlockRoot, source_epoch, source_root, target_epoch, target_root, crosslink);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttestationData testAttestationData = attestationData;

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttestationData testAttestationData =
        new AttestationData(
            beaconBlockRoot, source_epoch, source_root, target_epoch, target_root, crosslink);

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            Bytes32.random(), source_epoch, source_root, target_epoch, target_root, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSourceEpochsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            beaconBlockRoot,
            randomUnsignedLong(),
            source_root,
            target_epoch,
            target_root,
            crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSourceRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            beaconBlockRoot, source_epoch, Bytes32.random(), target_epoch, target_root, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenTargetEpochsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            beaconBlockRoot,
            source_epoch,
            source_root,
            randomUnsignedLong(),
            target_root,
            crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenTargetRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            beaconBlockRoot, source_epoch, source_root, target_epoch, Bytes32.random(), crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenLatestCrosslinkRootsAreDifferent() {

    AttestationData testAttestationData =
        new AttestationData(
            beaconBlockRoot,
            source_epoch,
            source_root,
            target_epoch,
            target_root,
            randomCrosslink());

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataBytes = attestationData.toBytes();
    assertEquals(attestationData, AttestationData.fromBytes(sszAttestationDataBytes));
  }
}
