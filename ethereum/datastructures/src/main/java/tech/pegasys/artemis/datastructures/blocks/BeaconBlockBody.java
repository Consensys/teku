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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;

/** A Beacon block body */
public class BeaconBlockBody {
  private List<ProposerSlashing> proposer_slashings;
  private List<AttesterSlashing> attester_slashings;
  private List<Attestation> attestations;
  private List<Deposit> deposits;
  private List<VoluntaryExit> voluntaryExits;
  private List<Transfer> transfers;

  public BeaconBlockBody(
      List<ProposerSlashing> proposer_slashings,
      List<AttesterSlashing> attester_slashings,
      List<Attestation> attestations,
      List<Deposit> deposits,
      List<VoluntaryExit> voluntaryExits,
      List<Transfer> transfers) {
    this.proposer_slashings = proposer_slashings;
    this.attester_slashings = attester_slashings;
    this.attestations = attestations;
    this.deposits = deposits;
    this.voluntaryExits = voluntaryExits;
    this.transfers = transfers;
  }

  public static BeaconBlockBody fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlockBody(
                reader.readBytesList().stream()
                    .map(ProposerSlashing::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(AttesterSlashing::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(Attestation::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(Deposit::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(VoluntaryExit::fromBytes)
                    .collect(Collectors.toList()),
                reader.readBytesList().stream()
                    .map(Transfer::fromBytes)
                    .collect(Collectors.toList())));
  }

  public Bytes toBytes() {
    List<Bytes> proposerSlashingsBytes =
        proposer_slashings.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> attesterSlashingsBytes =
        attester_slashings.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> attestationsBytes =
        attestations.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> depositsBytes =
        deposits.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> voluntaryExitsBytes =
        voluntaryExits.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> transfersBytes =
        transfers.stream().map(item -> item.toBytes()).collect(Collectors.toList());

    return SSZ.encode(
        writer -> {
          writer.writeBytesList(proposerSlashingsBytes);
          writer.writeBytesList(attesterSlashingsBytes);
          writer.writeBytesList(attestationsBytes);
          writer.writeBytesList(depositsBytes);
          writer.writeBytesList(voluntaryExitsBytes);
          writer.writeBytesList(transfersBytes);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        proposer_slashings, attester_slashings, attestations, deposits, voluntaryExits, transfers);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlockBody)) {
      return false;
    }

    BeaconBlockBody other = (BeaconBlockBody) obj;
    return Objects.equals(this.getProposer_slashings(), other.getProposer_slashings())
        && Objects.equals(this.getAttester_slashings(), other.getAttester_slashings())
        && Objects.equals(this.getAttestations(), other.getAttestations())
        && Objects.equals(this.getDeposits(), other.getDeposits())
        && Objects.equals(this.getVoluntaryExits(), other.getVoluntaryExits())
        && Objects.equals(this.getTransfers(), other.getTransfers());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Attestation> getAttestations() {
    return attestations;
  }

  public void setAttestations(List<Attestation> attestations) {
    this.attestations = attestations;
  }

  public List<ProposerSlashing> getProposer_slashings() {
    return proposer_slashings;
  }

  public void setProposer_slashings(List<ProposerSlashing> proposer_slashings) {
    this.proposer_slashings = proposer_slashings;
  }

  public List<AttesterSlashing> getAttester_slashings() {
    return attester_slashings;
  }

  public void setAttester_slashings(List<AttesterSlashing> attester_slashings) {
    this.attester_slashings = attester_slashings;
  }

  public List<Deposit> getDeposits() {
    return deposits;
  }

  public void setDeposits(List<Deposit> deposits) {
    this.deposits = deposits;
  }

  public List<VoluntaryExit> getVoluntaryExits() {
    return voluntaryExits;
  }

  public void setVoluntaryExits(List<VoluntaryExit> voluntaryExits) {
    this.voluntaryExits = voluntaryExits;
  }

  public List<Transfer> getTransfers() {
    return transfers;
  }

  public void setTransfers(List<Transfer> transfers) {
    this.transfers = transfers;
  }
}
