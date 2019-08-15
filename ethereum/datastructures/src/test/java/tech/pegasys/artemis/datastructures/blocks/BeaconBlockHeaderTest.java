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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.bls.BLSSignature;

class BeaconBlockHeaderTest {

  private UnsignedLong slot = randomUnsignedLong();
  private Bytes32 previous_block_root = Bytes32.random();
  private Bytes32 state_root = Bytes32.random();
  private Bytes32 block_body_root = Bytes32.random();
  private BLSSignature signature = BLSSignature.random();

  private BeaconBlockHeader beaconBlockHeader =
      new BeaconBlockHeader(slot, previous_block_root, state_root, block_body_root, signature);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BeaconBlockHeader testBeaconBlockHeaderSignedData = beaconBlockHeader;

    assertEquals(beaconBlockHeader, testBeaconBlockHeaderSignedData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(slot, previous_block_root, state_root, block_body_root, signature);

    assertEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot.plus(randomUnsignedLong()),
            previous_block_root,
            state_root,
            block_body_root,
            signature);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenPreviousBlockRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, previous_block_root.not(), state_root, block_body_root, signature);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, previous_block_root, state_root.not(), block_body_root, signature);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenBlockBodyRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, previous_block_root, state_root, block_body_root.not(), signature);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenSignaturesAreDifferent() {
    BLSSignature differentSignature = BLSSignature.random();
    while (differentSignature.equals(signature)) {
      differentSignature = BLSSignature.random();
    }

    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, previous_block_root, state_root, block_body_root, differentSignature);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszBeaconBlockHeaderBytes = beaconBlockHeader.toBytes();
    assertEquals(beaconBlockHeader, BeaconBlockHeader.fromBytes(sszBeaconBlockHeaderBytes));
  }

  @Test
  void blockRootHeaderRootMatchingTests() {
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(90000000);
    BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            block.getSlot(),
            block.getParent_root(),
            block.getState_root(),
            block.getBody().hash_tree_root(),
            block.getSignature());
    assertEquals(block.signing_root("signature"), blockHeader.signing_root("signature"));
    assertEquals(block.hash_tree_root(), blockHeader.hash_tree_root());
  }

}
