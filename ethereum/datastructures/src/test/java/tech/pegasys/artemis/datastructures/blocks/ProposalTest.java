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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSSignature;

class ProposalTest {

  private long slot = randomLong();
  private long shard = randomLong();
  private Bytes32 blockRoot = Bytes32.random();
  private BLSSignature signature = BLSSignature.random();

  private Proposal proposal = new Proposal(slot, shard, blockRoot, signature);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Proposal testProposalSignedData = proposal;

    assertEquals(proposal, testProposalSignedData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Proposal testProposal = new Proposal(slot, shard, blockRoot, signature);

    assertEquals(proposal, testProposal);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    Proposal testProposal = new Proposal(slot + randomLong(), shard, blockRoot, signature);

    assertNotEquals(proposal, testProposal);
  }

  @Test
  void equalsReturnsFalseWhenShardsAreDifferent() {
    Proposal testProposal = new Proposal(slot, shard + randomLong(), blockRoot, signature);

    assertNotEquals(proposal, testProposal);
  }

  @Test
  void equalsReturnsFalseWhenBlockRootsAreDifferent() {
    Proposal testProposal = new Proposal(slot, shard, blockRoot.not(), signature);

    assertNotEquals(proposal, testProposal);
  }

  @Test
  void equalsReturnsFalseWhenSignaturesAreDifferent() {
    BLSSignature differentSignature = BLSSignature.random();
    while (differentSignature.equals(signature)) {
      differentSignature = BLSSignature.random();
    }

    Proposal testProposal = new Proposal(slot, shard, blockRoot, differentSignature);

    assertNotEquals(proposal, testProposal);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszProposalBytes = proposal.toBytes();
    assertEquals(proposal, Proposal.fromBytes(sszProposalBytes));
  }
}
