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

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.Proposal;

public class ProposerSlashing {

  private UnsignedLong proposer_index;
  private Proposal proposal_1;
  private Proposal proposal_2;

  public ProposerSlashing(UnsignedLong proposer_index, Proposal proposal_1, Proposal proposal_2) {
    this.proposer_index = proposer_index;
    this.proposal_1 = proposal_1;
    this.proposal_2 = proposal_2;
  }

  public static ProposerSlashing fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new ProposerSlashing(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Proposal.fromBytes(reader.readBytes()),
                Proposal.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(proposer_index.longValue());
          writer.writeBytes(proposal_1.toBytes());
          writer.writeBytes(proposal_2.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(proposer_index, proposal_1, proposal_2);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof ProposerSlashing)) {
      return false;
    }

    ProposerSlashing other = (ProposerSlashing) obj;
    return Objects.equals(this.getProposer_index(), other.getProposer_index())
        && Objects.equals(this.getProposal_1(), other.getProposal_1())
        && Objects.equals(this.getProposal_2(), other.getProposal_2());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getProposer_index() {
    return proposer_index;
  }

  public void setProposer_index(UnsignedLong proposer_index) {
    this.proposer_index = proposer_index;
  }

  public Proposal getProposal_1() {
    return proposal_1;
  }

  public void setProposal_1(Proposal proposal_1) {
    this.proposal_1 = proposal_1;
  }

  public Proposal getProposal_2() {
    return proposal_2;
  }

  public void setProposal_2(Proposal proposal_2) {
    this.proposal_2 = proposal_2;
  }
}
