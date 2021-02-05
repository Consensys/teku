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

package tech.pegasys.teku.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

class ProposerSlashingTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private SignedBeaconBlockHeader proposal1 = dataStructureUtil.randomSignedBeaconBlockHeader();
  private SignedBeaconBlockHeader proposal2 = dataStructureUtil.randomSignedBeaconBlockHeader();

  private ProposerSlashing proposerSlashing = new ProposerSlashing(proposal1, proposal2);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    ProposerSlashing testProposerSlashing = proposerSlashing;

    assertEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    ProposerSlashing testProposerSlashing = new ProposerSlashing(proposal1, proposal2);

    assertEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalData1IsDifferent() {
    // Proposal is rather involved to create. Just create a random one until it is not the
    // same as the original.
    SignedBeaconBlockHeader otherProposal1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    while (Objects.equals(otherProposal1, proposal1)) {
      otherProposal1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    }

    ProposerSlashing testProposerSlashing = new ProposerSlashing(otherProposal1, proposal2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalData2IsDifferent() {
    // Proposal is rather involved to create. Just create a random one until it is not the
    // same as the original.
    SignedBeaconBlockHeader otherProposal2 = dataStructureUtil.randomSignedBeaconBlockHeader();
    while (Objects.equals(otherProposal2, proposal2)) {
      otherProposal2 = dataStructureUtil.randomSignedBeaconBlockHeader();
    }

    ProposerSlashing testProposerSlashing = new ProposerSlashing(proposal1, otherProposal2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszProposerSlashingBytes = proposerSlashing.sszSerialize();
    assertEquals(proposerSlashing, ProposerSlashing.TYPE.sszDeserialize(sszProposerSlashingBytes));
  }
}
