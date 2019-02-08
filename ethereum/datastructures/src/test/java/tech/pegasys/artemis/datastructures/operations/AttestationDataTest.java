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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class AttestationDataTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 = ad1;

    assertEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);

    assertEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot + randomLong(),
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenShardsAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard.plus(randomUnsignedLong()),
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenBeaconBlockHashesAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash.not(),
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenEpochBoundaryHashesAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash.not(),
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenShardBlockHashesAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash.not(),
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenLastCrosslinkHashesAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash.not(),
            justifiedSlot,
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenJustifiedSlotsAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot.plus(randomUnsignedLong()),
            justifiedBlockHash);

    assertNotEquals(ad1, ad2);
  }

  @Test
  void equalsReturnsFalseWhenJustifiedBlockHashesAreDifferent() {
    long slot = randomLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 beaconBlockHash = Bytes32.random();
    Bytes32 epochBoundaryHash = Bytes32.random();
    Bytes32 shardBlockHash = Bytes32.random();
    Bytes32 lastCrosslinkHash = Bytes32.random();
    UnsignedLong justifiedSlot = randomUnsignedLong();
    Bytes32 justifiedBlockHash = Bytes32.random();

    AttestationData ad1 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash);
    AttestationData ad2 =
        new AttestationData(
            slot,
            shard,
            beaconBlockHash,
            epochBoundaryHash,
            shardBlockHash,
            lastCrosslinkHash,
            justifiedSlot,
            justifiedBlockHash.not());

    assertNotEquals(ad1, ad2);
  }

  @Test
  void rountripSSZ() {
    AttestationData attestationData =
        new AttestationData(
            randomLong(),
            randomUnsignedLong(),
            Bytes32.random(),
            Bytes32.random(),
            Bytes32.random(),
            Bytes32.random(),
            randomUnsignedLong(),
            Bytes32.random());
    Bytes sszAttestationDataBytes = attestationData.toBytes();
    assertEquals(attestationData, AttestationData.fromBytes(sszAttestationDataBytes));
  }
}
