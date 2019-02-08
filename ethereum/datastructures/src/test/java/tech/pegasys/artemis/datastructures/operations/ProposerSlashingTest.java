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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;

class ProposerSlashingTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 = ps1;

    assertEquals(ps1, ps2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);

    assertEquals(ps1, ps2);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndicesAreDifferent() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 =
        new ProposerSlashing(
            proposerIndex + randomInt(),
            proposalData1,
            proposalSignature1,
            proposalData2,
            proposalSignature2);

    assertNotEquals(ps1, ps2);
  }

  @Test
  void equalsReturnsFalseWhenProposalData1IsDifferent() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    // ProposalSignedData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    ProposalSignedData otherProposalData1 = randomProposalSignedData();
    while (Objects.equals(otherProposalData1, proposalData1)) {
      otherProposalData1 = randomProposalSignedData();
    }

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 =
        new ProposerSlashing(
            proposerIndex,
            otherProposalData1,
            proposalSignature1,
            proposalData2,
            proposalSignature2);

    assertNotEquals(ps1, ps2);
  }

  @Test
  void equalsReturnsFalseWhenProposalSignature1sAreDifferent() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of proposalSignature1 and reverse to ensure it is different.
    List<Bytes48> reverseProposalSignature1 = new ArrayList<Bytes48>(proposalSignature1);
    Collections.reverse(reverseProposalSignature1);

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 =
        new ProposerSlashing(
            proposerIndex,
            proposalData1,
            reverseProposalSignature1,
            proposalData2,
            proposalSignature2);

    assertNotEquals(ps1, ps2);
  }

  @Test
  void equalsReturnsFalseWhenProposalData2IsDifferent() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    // ProposalSignedData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    ProposalSignedData otherProposalData2 = randomProposalSignedData();
    while (Objects.equals(otherProposalData2, proposalData2)) {
      otherProposalData2 = randomProposalSignedData();
    }

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 =
        new ProposerSlashing(
            proposerIndex,
            proposalData1,
            proposalSignature1,
            otherProposalData2,
            proposalSignature2);

    assertNotEquals(ps1, ps2);
  }

  @Test
  void equalsReturnsFalseWhenProposalSignature2sAreDifferent() {
    int proposerIndex = randomInt();
    ProposalSignedData proposalData1 = randomProposalSignedData();
    List<Bytes48> proposalSignature1 = Arrays.asList(Bytes48.random(), Bytes48.random());
    ProposalSignedData proposalData2 = randomProposalSignedData();
    List<Bytes48> proposalSignature2 = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of proposalSignature1 and reverse to ensure it is different.
    List<Bytes48> reverseProposalSignature2 = new ArrayList<Bytes48>(proposalSignature2);
    Collections.reverse(reverseProposalSignature2);

    ProposerSlashing ps1 =
        new ProposerSlashing(
            proposerIndex, proposalData1, proposalSignature1, proposalData2, proposalSignature2);
    ProposerSlashing ps2 =
        new ProposerSlashing(
            proposerIndex,
            proposalData1,
            proposalSignature1,
            proposalData2,
            reverseProposalSignature2);

    assertNotEquals(ps1, ps2);
  }

  @Test
  void rountripSSZ() {
    ProposerSlashing proposerSlashing =
        new ProposerSlashing(
            randomInt(),
            randomProposalSignedData(),
            Arrays.asList(Bytes48.random(), Bytes48.random()),
            randomProposalSignedData(),
            Arrays.asList(Bytes48.random(), Bytes48.random()));
    Bytes sszProposerSlashingBytes = proposerSlashing.toBytes();
    assertEquals(proposerSlashing, ProposerSlashing.fromBytes(sszProposerSlashingBytes));
  }

  private int randomInt() {
    return (int) (Math.random() * 1000000);
  }

  private long randomLong() {
    return Math.round(Math.random() * 1000000);
  }

  private UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
  }

  private ProposalSignedData randomProposalSignedData() {
    return new ProposalSignedData(randomUnsignedLong(), randomUnsignedLong(), Bytes32.random());
  }
}
