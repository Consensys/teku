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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

/** A Beacon block body */
public class BeaconBlockBody implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  private BLSSignature randao_reveal;
  private Eth1Data eth1_data;
  private Bytes32 graffiti;
  private List<ProposerSlashing> proposer_slashings;
  private List<AttesterSlashing> attester_slashings;
  private List<Attestation> attestations;
  private List<Deposit> deposits;
  private List<VoluntaryExit> voluntary_exits;
  private List<Transfer> transfers;

  public BeaconBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      List<ProposerSlashing> proposer_slashings,
      List<AttesterSlashing> attester_slashings,
      List<Attestation> attestations,
      List<Deposit> deposits,
      List<VoluntaryExit> voluntary_exits,
      List<Transfer> transfers) {
    this.randao_reveal = randao_reveal;
    this.eth1_data = eth1_data;
    this.graffiti = graffiti;
    this.proposer_slashings = proposer_slashings;
    this.attester_slashings = attester_slashings;
    this.attestations = attestations;
    this.deposits = deposits;
    this.voluntary_exits = voluntary_exits;
    this.transfers = transfers;
  }

  public BeaconBlockBody() {
    this.randao_reveal = BLSSignature.empty();
    this.eth1_data = new Eth1Data();
    this.graffiti = Bytes32.ZERO;
    this.proposer_slashings = new ArrayList<>();
    this.attester_slashings = new ArrayList<>();
    this.attestations = new ArrayList<>();
    this.deposits = new ArrayList<>();
    this.voluntary_exits = new ArrayList<>();
    this.transfers = new ArrayList<>();
  }

  @Override
  public int getSSZFieldCount() {
    // TODO Finish this stub.
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  @Override
  public List<Bytes> get_variable_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  public static BeaconBlockBody fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlockBody(
                BLSSignature.fromBytes(reader.readBytes()),
                Eth1Data.fromBytes(reader.readBytes()),
                Bytes32.wrap(reader.readFixedBytes(32)),
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
        voluntary_exits.stream().map(item -> item.toBytes()).collect(Collectors.toList());
    List<Bytes> transfersBytes =
        transfers.stream().map(item -> item.toBytes()).collect(Collectors.toList());

    return SSZ.encode(
        writer -> {
          writer.writeBytes(randao_reveal.toBytes());
          writer.writeBytes(eth1_data.toBytes());
          writer.writeFixedBytes(graffiti);
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
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits,
        transfers);
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
    return Objects.equals(this.getRandao_reveal(), other.getRandao_reveal())
        && Objects.equals(this.getEth1_data(), other.getEth1_data())
        && Objects.equals(this.getGraffiti(), other.getGraffiti())
        && Objects.equals(this.getProposer_slashings(), other.getProposer_slashings())
        && Objects.equals(this.getAttester_slashings(), other.getAttester_slashings())
        && Objects.equals(this.getAttestations(), other.getAttestations())
        && Objects.equals(this.getDeposits(), other.getDeposits())
        && Objects.equals(this.getVoluntary_exits(), other.getVoluntary_exits())
        && Objects.equals(this.getTransfers(), other.getTransfers());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BLSSignature getRandao_reveal() {
    return randao_reveal;
  }

  public void setRandao_reveal(BLSSignature randao_reveal) {
    this.randao_reveal = randao_reveal;
  }

  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  public Bytes32 getGraffiti() {
    return graffiti;
  }

  public void setGraffiti(Bytes32 graffiti) {
    this.graffiti = graffiti;
  }

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

  public List<VoluntaryExit> getVoluntary_exits() {
    return voluntary_exits;
  }

  public void setVoluntary_exits(List<VoluntaryExit> voluntary_exits) {
    this.voluntary_exits = voluntary_exits;
  }

  public List<Transfer> getTransfers() {
    return transfers;
  }

  public void setTransfers(List<Transfer> transfers) {
    this.transfers = transfers;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, randao_reveal.toBytes()),
            eth1_data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, graffiti),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, proposer_slashings),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, attester_slashings),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, attestations),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, deposits),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, voluntary_exits),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, transfers)));
  }
}
