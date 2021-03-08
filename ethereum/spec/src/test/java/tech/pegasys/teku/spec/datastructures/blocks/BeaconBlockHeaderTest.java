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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconBlockHeaderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private UInt64 slot = dataStructureUtil.randomUInt64();
  private UInt64 proposer_index = dataStructureUtil.randomUInt64();
  private Bytes32 previous_block_root = dataStructureUtil.randomBytes32();
  private Bytes32 state_root = dataStructureUtil.randomBytes32();
  private Bytes32 block_body_root = dataStructureUtil.randomBytes32();

  private BeaconBlockHeader beaconBlockHeader =
      new BeaconBlockHeader(slot, proposer_index, previous_block_root, state_root, block_body_root);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BeaconBlockHeader testBeaconBlockHeaderSignedData = beaconBlockHeader;

    assertEquals(beaconBlockHeader, testBeaconBlockHeaderSignedData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposer_index, previous_block_root, state_root, block_body_root);

    assertEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot.plus(dataStructureUtil.randomUInt64()),
            proposer_index,
            previous_block_root,
            state_root,
            block_body_root);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndexIsDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot,
            proposer_index.plus(dataStructureUtil.randomUInt64()),
            previous_block_root,
            state_root,
            block_body_root);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenPreviousBlockRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposer_index, previous_block_root.not(), state_root, block_body_root);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposer_index, previous_block_root, state_root.not(), block_body_root);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenBlockBodyRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposer_index, previous_block_root, state_root, block_body_root.not());

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void roundtripSSZ() {
    BeaconBlockHeader beaconBlockHeader = dataStructureUtil.randomBeaconBlockHeader();
    Bytes beaconBlockSerialized = beaconBlockHeader.sszSerialize();
    BeaconBlockHeader newBeaconBlockHeader =
        BeaconBlockHeader.SSZ_SCHEMA.sszDeserialize(beaconBlockSerialized);
    assertEquals(beaconBlockHeader, newBeaconBlockHeader);
  }

  @Test
  void blockRootHeaderRootMatchingTests() {
    BeaconBlock block = dataStructureUtil.randomBeaconBlock(90000000);
    BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            block.getBody().hashTreeRoot());
    assertEquals(block.hashTreeRoot(), blockHeader.hashTreeRoot());
  }
}
