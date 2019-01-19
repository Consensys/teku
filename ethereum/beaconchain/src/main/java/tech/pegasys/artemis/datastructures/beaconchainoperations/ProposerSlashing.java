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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import net.consensys.cava.bytes.Bytes48;
import tech.pegasys.artemis.datastructures.beaconchainblocks.ProposalSignedData;

public class ProposerSlashing {

  private int proposer_index;
  private ProposalSignedData proposal_data_1;
  private Bytes48[] proposal_signature_1;
  private ProposalSignedData proposal_data_2;
  private Bytes48[] proposal_signature_2;

  public ProposerSlashing(
      int proposer_index,
      ProposalSignedData proposal_data_1,
      Bytes48[] proposal_signature_1,
      ProposalSignedData proposal_data_2,
      Bytes48[] proposal_signature_2) {
    this.proposer_index = proposer_index;
    this.proposal_data_1 = proposal_data_1;
    this.proposal_signature_1 = proposal_signature_1;
    this.proposal_data_2 = proposal_data_2;
    this.proposal_signature_2 = proposal_signature_2;
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

  public Bytes48[] getProposal_signature_1() {
    return proposal_signature_1;
  }

  public void setProposal_signature_1(Bytes48[] proposal_signature_1) {
    this.proposal_signature_1 = proposal_signature_1;
  }

  public ProposalSignedData getProposal_data_2() {
    return proposal_data_2;
  }

  public void setProposal_data_2(ProposalSignedData proposal_data_2) {
    this.proposal_data_2 = proposal_data_2;
  }

  public Bytes48[] getProposal_signature_2() {
    return proposal_signature_2;
  }

  public void setProposal_signature_2(Bytes48[] proposal_signature_2) {
    this.proposal_signature_2 = proposal_signature_2;
  }
}
