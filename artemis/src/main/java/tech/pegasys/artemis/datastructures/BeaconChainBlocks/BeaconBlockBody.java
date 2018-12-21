/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.BeaconChainBlocks;

import tech.pegasys.artemis.datastructures.BeaconChainOperations.Attestation;
import tech.pegasys.artemis.datastructures.BeaconChainOperations.CasperSlashing;
import tech.pegasys.artemis.datastructures.BeaconChainOperations.Deposit;
import tech.pegasys.artemis.datastructures.BeaconChainOperations.Exit;
import tech.pegasys.artemis.datastructures.BeaconChainOperations.ProposerSlashing;

public class BeaconBlockBody {

  private Attestation[] attestations;
  private ProposerSlashing[] proposer_slashings;
  private CasperSlashing[] casper_slashings;
  private Deposit[] deposits;
  private Exit[] exits;

  public BeaconBlockBody(Attestation[] attestations, ProposerSlashing[] proposer_slashings, CasperSlashing[]
                         casper_slashings, Deposit[] deposits, Exit[] exits) {
    this.attestations = attestations;
    this.proposer_slashings = proposer_slashings;
    this.casper_slashings = casper_slashings;
    this.deposits = deposits;
    this.exits = exits;
  }


  public Attestation[] getAttestations() {
    return attestations;
  }

  public void setAttestations(Attestation[] attestations) {
    this.attestations = attestations;
  }

  public ProposerSlashing[] getProposer_slashings() {
    return proposer_slashings;
  }

  public void setProposer_slashings(ProposerSlashing[] proposer_slashings) {
    this.proposer_slashings = proposer_slashings;
  }

  public CasperSlashing[] getCasper_slashings() {
    return casper_slashings;
  }

  public void setCasper_slashings(CasperSlashing[] casper_slashings) {
    this.casper_slashings = casper_slashings;
  }

  public Deposit[] getDeposits() {
    return deposits;
  }

  public void setDeposits(Deposit[] deposits) {
    this.deposits = deposits;
  }

  public Exit[] getExits() {
    return exits;
  }

  public void setExits(Exit[] exits) {
    this.exits = exits;
  }
}
