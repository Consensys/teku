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
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Crosslink;

class AttestationDataTest {

  private Bytes32 beaconBlockRoot = Bytes32.random();
  private UnsignedLong source_epoch = randomUnsignedLong();
  private Bytes32 source_root = Bytes32.random();
  private UnsignedLong target_epoch = randomUnsignedLong();
  private Bytes32 target_root = Bytes32.random();
  private Checkpoint source = new Checkpoint(source_epoch, source_root);
  private Checkpoint target = new Checkpoint(target_epoch, target_root);
  private Crosslink crosslink = randomCrosslink();

  private AttestationData attestationData =
      new AttestationData(beaconBlockRoot, source, target, crosslink);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttestationData testAttestationData = attestationData;

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttestationData testAttestationData =
        new AttestationData(beaconBlockRoot, source, target, crosslink);

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(Bytes32.random(), source, target, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSourceEpochsAreDifferent() {
    Checkpoint newSource = new Checkpoint(source);
    newSource.setEpoch(randomUnsignedLong());
    AttestationData testAttestationData =
        new AttestationData(beaconBlockRoot, newSource, target, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSourceRootsAreDifferent() {
    Checkpoint newSource = new Checkpoint(source);
    newSource.setRoot(Bytes32.random());
    AttestationData testAttestationData =
        new AttestationData(beaconBlockRoot, source, target, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenTargetEpochsAreDifferent() {
    Checkpoint newTarget = new Checkpoint(target);
    newTarget.setEpoch(randomUnsignedLong());
    AttestationData testAttestationData =
        new AttestationData(beaconBlockRoot, source, target, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenTargetRootsAreDifferent() {
    Checkpoint newTarget = new Checkpoint(target);
    newTarget.setRoot(Bytes32.random());
    AttestationData testAttestationData =
        new AttestationData(beaconBlockRoot, source, target, crosslink);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenLatestCrosslinkRootsAreDifferent() {

    AttestationData testAttestationData =
        new AttestationData(beaconBlockRoot, source, target, randomCrosslink());

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataBytes = attestationData.toBytes();
    assertEquals(attestationData, AttestationData.fromBytes(sszAttestationDataBytes));
  }
}
