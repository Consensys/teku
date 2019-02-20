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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomProposalSignedData;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.util.bls.BLSSignature;

class ProposerSlashingTest {

  private UnsignedLong proposerIndex = randomUnsignedLong();
  private ProposalSignedData proposalData1 = randomProposalSignedData();
  private BLSSignature proposalSignature1 = BLSSignature.random();
  private ProposalSignedData proposalData2 = randomProposalSignedData();
  private BLSSignature proposalSignature2 = BLSSignature.random();

  private ProposerSlashing proposerSlashing =
      new ProposerSlashing(
          proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    ProposerSlashing testProposerSlashing = proposerSlashing;

    assertEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);

    assertEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndicesAreDifferent() {
    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(
            proposerIndex.plus(randomUnsignedLong()),
            proposalData1,
            proposalSignature1,
            proposalData2,
            proposalSignature2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalData1IsDifferent() {
    // ProposalSignedData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    ProposalSignedData otherProposalData1 = randomProposalSignedData();
    while (Objects.equals(otherProposalData1, proposalData1)) {
      otherProposalData1 = randomProposalSignedData();
    }

    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(
            proposerIndex,
            otherProposalData1,
            proposalSignature1,
            proposalData2,
            proposalSignature2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalSignature1sAreDifferent() {
    BLSSignature differentProposalSignature1 = BLSSignature.random();
    while (differentProposalSignature1.equals(proposalSignature2)) {
      differentProposalSignature1 = BLSSignature.random();
    }

    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(
            proposerIndex,
            proposalData1,
            differentProposalSignature1,
            proposalData2,
            proposalSignature2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalData2IsDifferent() {
    // ProposalSignedData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    ProposalSignedData otherProposalData2 = randomProposalSignedData();
    while (Objects.equals(otherProposalData2, proposalData2)) {
      otherProposalData2 = randomProposalSignedData();
    }

    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(
            proposerIndex,
            proposalData1,
            proposalSignature1,
            otherProposalData2,
            proposalSignature2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalSignature2sAreDifferent() {
    BLSSignature differentProposalSignature2 = BLSSignature.random();
    while (differentProposalSignature2.equals(proposalSignature2)) {
      differentProposalSignature2 = BLSSignature.random();
    }

    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(
            proposerIndex,
            proposalData1,
            proposalSignature1,
            proposalData2,
            differentProposalSignature2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void rountripSSZ() {
    Bytes sszProposerSlashingBytes = proposerSlashing.toBytes();
    assertEquals(proposerSlashing, ProposerSlashing.fromBytes(sszProposerSlashingBytes));
  }
}
