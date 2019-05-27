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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomInt;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Crosslink;

class AttestationDataTest {

  private UnsignedLong slot = randomUnsignedLong();
  private Bytes32 beaconBlockRoot = Bytes32.random();
  private UnsignedLong shard = randomUnsignedLong();
  private Bytes32 epochBoundaryRoot = Bytes32.random();
  private Bytes32 crosslinkDataRoot = Bytes32.random();
  private UnsignedLong justifiedEpoch = randomUnsignedLong();
  private Crosslink latestCrosslink = randomCrosslink();
  private Bytes32 justifiedBlockRoot = Bytes32.random();

  private final AttestationData attestationData =
      new AttestationData(
          slot,
          beaconBlockRoot,
          shard,
          epochBoundaryRoot,
          crosslinkDataRoot,
          justifiedEpoch,
          latestCrosslink,
          justifiedBlockRoot);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttestationData testAttestationData = attestationData;

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot);

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot.plus(randomUnsignedLong()),
            beaconBlockRoot,
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenShardsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard.plus(randomUnsignedLong()),
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenBeaconBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot.not(),
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenEpochBoundaryRootAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard,
            epochBoundaryRoot.not(),
            crosslinkDataRoot,
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenCrosslinkDataRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot.not(),
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenLatestCrosslinkRootsAreDifferent() {
    Crosslink diffCrosslink =
        new Crosslink(
            latestCrosslink.getEpoch() + randomLong(),
            Bytes32.wrap(latestCrosslink.getCrosslink_data_root(), randomInt(0)));

    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch,
            diffCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenJustifiedEpochsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch.plus(randomUnsignedLong()),
            latestCrosslink,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenJustifiedBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            beaconBlockRoot,
            shard,
            epochBoundaryRoot,
            crosslinkDataRoot,
            justifiedEpoch,
            latestCrosslink,
            justifiedBlockRoot.not());

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataBytes = attestationData.toBytes();
    assertEquals(attestationData, AttestationData.fromBytes(sszAttestationDataBytes));
  }
}
