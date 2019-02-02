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

import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;

public class ProposerSlashing {

  private int proposer_index;
  private ProposalSignedData proposal_data_1;
  private List<Bytes48> proposal_signature_1;
  private ProposalSignedData proposal_data_2;
  private List<Bytes48> proposal_signature_2;

  public ProposerSlashing(
      int proposer_index,
      ProposalSignedData proposal_data_1,
      List<Bytes48> proposal_signature_1,
      ProposalSignedData proposal_data_2,
      List<Bytes48> proposal_signature_2) {
    this.proposer_index = proposer_index;
    this.proposal_data_1 = proposal_data_1;
    this.proposal_signature_1 = proposal_signature_1;
    this.proposal_data_2 = proposal_data_2;
    this.proposal_signature_2 = proposal_signature_2;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeInt(proposer_index, 24);
          writer.writeBytes(proposal_data_1.toBytes());
          writer.writeBytesList(proposal_signature_1.toArray(new Bytes48[0]));
          writer.writeBytes(proposal_data_2.toBytes());
          writer.writeBytesList(proposal_signature_2.toArray(new Bytes48[0]));
        });
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public int getProposer_index() {
    return proposer_index;
  }

  public void setProposer_index(int proposer_index) {
    this.proposer_index = proposer_index;
  }

  public ProposalSignedData getProposal_data_1() {
    return proposal_data_1;
  }

  public void setProposal_data_1(ProposalSignedData proposal_data_1) {
    this.proposal_data_1 = proposal_data_1;
  }

  public List<Bytes48> getProposal_signature_1() {
    return proposal_signature_1;
  }

  public void setProposal_signature_1(List<Bytes48> proposal_signature_1) {
    this.proposal_signature_1 = proposal_signature_1;
  }

  public ProposalSignedData getProposal_data_2() {
    return proposal_data_2;
  }

  public void setProposal_data_2(ProposalSignedData proposal_data_2) {
    this.proposal_data_2 = proposal_data_2;
  }

  public List<Bytes48> getProposal_signature_2() {
    return proposal_signature_2;
  }

  public void setProposal_signature_2(List<Bytes48> proposal_signature_2) {
    this.proposal_signature_2 = proposal_signature_2;
  }
}
