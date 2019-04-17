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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBeaconBlockHeader;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;

class ProposerSlashingTest {

  private UnsignedLong proposerIndex = randomUnsignedLong();
  private BeaconBlockHeader proposal1 = randomBeaconBlockHeader();
  private BeaconBlockHeader proposal2 = randomBeaconBlockHeader();

  private ProposerSlashing proposerSlashing =
      new ProposerSlashing(proposerIndex, proposal1, proposal2);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    ProposerSlashing testProposerSlashing = proposerSlashing;

    assertEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(proposerIndex, proposal1, proposal2);

    assertEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndicesAreDifferent() {
    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(proposerIndex.plus(randomUnsignedLong()), proposal1, proposal2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalData1IsDifferent() {
    // Proposalis rather involved to create. Just create a random one until it is not the
    // same as the original.
    BeaconBlockHeader otherProposal1 = randomBeaconBlockHeader();
    while (Objects.equals(otherProposal1, proposal1)) {
      otherProposal1 = randomBeaconBlockHeader();
    }

    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(proposerIndex, otherProposal1, proposal2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void equalsReturnsFalseWhenProposalData2IsDifferent() {
    // Proposal is rather involved to create. Just create a random one until it is not the
    // same as the original.
    BeaconBlockHeader otherProposal2 = randomBeaconBlockHeader();
    while (Objects.equals(otherProposal2, proposal2)) {
      otherProposal2 = randomBeaconBlockHeader();
    }

    ProposerSlashing testProposerSlashing =
        new ProposerSlashing(proposerIndex, proposal1, otherProposal2);

    assertNotEquals(proposerSlashing, testProposerSlashing);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszProposerSlashingBytes = proposerSlashing.toBytes();
    assertEquals(proposerSlashing, ProposerSlashing.fromBytes(sszProposerSlashingBytes));
  }
}
