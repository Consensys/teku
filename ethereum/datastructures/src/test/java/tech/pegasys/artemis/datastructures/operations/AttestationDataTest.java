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

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Crosslink;

class AttestationDataTest {

  private long slot = randomLong();
  private long shard = randomLong();
  private Bytes32 beaconBlockRoot = Bytes32.random();
  private Bytes32 epochBoundaryRoot = Bytes32.random();
  private Bytes32 crosslinkDataRoot = Bytes32.random();
  private Crosslink latestCrosslink = randomCrosslink();
  private long justifiedEpoch = randomLong();
  private Bytes32 justifiedBlockRoot = Bytes32.random();

  private AttestationData attestationData =
      new AttestationData(
          slot,
          shard,
          beaconBlockRoot,
          epochBoundaryRoot,
          crosslinkDataRoot,
          latestCrosslink,
          justifiedEpoch,
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
            shard,
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch,
            justifiedBlockRoot);

    assertEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot + randomLong(),
            shard,
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenShardsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            shard + randomLong(),
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenBeaconBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            shard,
            beaconBlockRoot.not(),
            epochBoundaryRoot,
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenEpochBoundaryRootAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            shard,
            beaconBlockRoot,
            epochBoundaryRoot.not(),
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenCrosslinkDataRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            shard,
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot.not(),
            latestCrosslink,
            justifiedEpoch,
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
            shard,
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot,
            diffCrosslink,
            justifiedEpoch,
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenJustifiedEpochsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            shard,
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch + randomLong(),
            justifiedBlockRoot);

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void equalsReturnsFalseWhenJustifiedBlockRootsAreDifferent() {
    AttestationData testAttestationData =
        new AttestationData(
            slot,
            shard,
            beaconBlockRoot,
            epochBoundaryRoot,
            crosslinkDataRoot,
            latestCrosslink,
            justifiedEpoch,
            justifiedBlockRoot.not());

    assertNotEquals(attestationData, testAttestationData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataBytes = attestationData.toBytes();
    assertEquals(attestationData, AttestationData.fromBytes(sszAttestationDataBytes));
  }
}
