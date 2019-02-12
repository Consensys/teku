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
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class ProposalSignedDataTest {

  private UnsignedLong slot = randomUnsignedLong();
  private UnsignedLong shard = randomUnsignedLong();
  private Bytes32 blockHash = Bytes32.random();

  private ProposalSignedData proposalSignedData = new ProposalSignedData(slot, shard, blockHash);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    ProposalSignedData testProposalSignedData = proposalSignedData;

    assertEquals(proposalSignedData, testProposalSignedData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    ProposalSignedData testProposalSignedData = new ProposalSignedData(slot, shard, blockHash);

    assertEquals(proposalSignedData, testProposalSignedData);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    ProposalSignedData testProposalSignedData =
        new ProposalSignedData(slot.plus(randomUnsignedLong()), shard, blockHash);

    assertNotEquals(proposalSignedData, testProposalSignedData);
  }

  @Test
  void equalsReturnsFalseWhenShardsAreDifferent() {
    ProposalSignedData testProposalSignedData =
        new ProposalSignedData(slot, shard.plus(randomUnsignedLong()), blockHash);

    assertNotEquals(proposalSignedData, testProposalSignedData);
  }

  @Test
  void equalsReturnsFalseWhenBlockHashesAreDifferent() {
    ProposalSignedData testProposalSignedData =
        new ProposalSignedData(slot, shard, blockHash.not());

    assertNotEquals(proposalSignedData, testProposalSignedData);
  }

  @Test
  void rountripSSZ() {
    Bytes sszProposalSignedDataBytes = proposalSignedData.toBytes();
    assertEquals(proposalSignedData, ProposalSignedData.fromBytes(sszProposalSignedDataBytes));
  }
}
