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

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.CasperSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProofOfCustodyChallenge;
import tech.pegasys.artemis.datastructures.operations.ProofOfCustodyResponse;
import tech.pegasys.artemis.datastructures.operations.ProofOfCustodySeedChange;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;

/** A Beacon block body */
public class BeaconBlockBody {

  private ProposerSlashing[] proposer_slashings;
  private CasperSlashing[] casper_slashings;
  private Attestation[] attestations;
  private Deposit[] deposits;
  private Exit[] exits;
  private ProofOfCustodySeedChange[] poc_seed_changes;
  private ProofOfCustodyChallenge[] poc_challenges;
  private ProofOfCustodyResponse[] poc_responses;

  public BeaconBlockBody(
      Attestation[] attestations,
      ProposerSlashing[] proposer_slashings,
      CasperSlashing[] casper_slashings,
      Deposit[] deposits,
      Exit[] exits) {
    this.attestations = attestations;
    this.proposer_slashings = proposer_slashings;
    this.casper_slashings = casper_slashings;
    this.deposits = deposits;
    this.exits = exits;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          for (ProposerSlashing item : proposer_slashings) {
            writer.writeBytes(item.toBytes());
          }
          for (CasperSlashing item : casper_slashings) {
            writer.writeBytes(item.toBytes());
          }
          for (Attestation item : attestations) {
            writer.writeBytes(item.toBytes());
          }
          for (Deposit item : deposits) {
            writer.writeBytes(item.toBytes());
          }
          for (Exit item : exits) {
            writer.writeBytes(item.toBytes());
          }
        });
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
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
