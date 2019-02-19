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

package tech.pegasys.artemis.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBeaconBlockBody;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomEth1Data;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSSignature;

class BeaconBlockTest {

  private long slot = randomLong();
  private Bytes32 parentRoot = Bytes32.random();
  private Bytes32 stateRoot = Bytes32.random();
  private BLSSignature randaoReveal = BLSSignature.random();
  private Eth1Data eth1Data = randomEth1Data();
  private BLSSignature signature = BLSSignature.random();
  private BeaconBlockBody body = randomBeaconBlockBody();

  private BeaconBlock beaconBlock =
      new BeaconBlock(slot, parentRoot, stateRoot, randaoReveal, eth1Data, signature, body);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    BeaconBlock testBeaconBlock = beaconBlock;

    assertEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(slot, parentRoot, stateRoot, randaoReveal, eth1Data, signature, body);

    assertEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(
            slot + randomLong(), parentRoot, stateRoot, randaoReveal, eth1Data, signature, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenParentRootsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(slot, parentRoot.not(), stateRoot, randaoReveal, eth1Data, signature, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(slot, parentRoot, stateRoot.not(), randaoReveal, eth1Data, signature, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  /* TODO: Fix this test
    @Test
    void equalsReturnsFalseWhenRandaoRevealsAreDifferent() {
      // Create copy of signature and reverse to ensure it is different.
      BLSSignature reverseRandaoReveal = new BLSSignature(randaoReveal.getC1(), randaoReveal.getC0());

      BeaconBlock testBeaconBlock =
          new BeaconBlock(
              slot, parentRoot, stateRoot, reverseRandaoReveal, eth1Data, signature, body);

      assertNotEquals(beaconBlock, testBeaconBlock);
    }
  */

  @Test
  void equalsReturnsFalseWhenEth1DataIsDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(
            slot,
            parentRoot,
            stateRoot,
            randaoReveal,
            new Eth1Data(eth1Data.getDeposit_root().not(), eth1Data.getBlock_root().not()),
            signature,
            body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  /* TODO: Fix this test
    @Test
    void equalsReturnsFalseWhenSignaturesAreDifferent() {
      // Create copy of signature and reverse to ensure it is different.
      BLSSignature reverseSignature = new BLSSignature(signature.getC1(), signature.getC0());

      BeaconBlock testBeaconBlock =
          new BeaconBlock(
              slot, parentRoot, stateRoot, randaoReveal, eth1Data, reverseSignature, body);

      assertNotEquals(beaconBlock, testBeaconBlock);
    }
  */

  @Test
  void equalsReturnsFalseWhenBeaconBlockBodiesAreDifferent() {
    // BeaconBlock is rather involved to create. Just create a random one until it is not the same
    // as the original.
    BeaconBlockBody otherBody = randomBeaconBlockBody();
    while (Objects.equals(otherBody, body)) {
      otherBody = randomBeaconBlockBody();
    }

    BeaconBlock testBeaconBlock =
        new BeaconBlock(slot, parentRoot, stateRoot, randaoReveal, eth1Data, signature, otherBody);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void rountripSSZ() {
    Bytes sszBeaconBlockBytes = beaconBlock.toBytes();
    assertEquals(beaconBlock, BeaconBlock.fromBytes(sszBeaconBlockBytes));
  }
}
