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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

/** A Beacon block body */
public class BeaconBlockBody implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 6;

  private final BLSSignature randao_reveal;
  private final Eth1Data eth1_data;
  private final Bytes32 graffiti;
  private final SSZList<ProposerSlashing>
      proposer_slashings; // List bounded by MAX_PROPOSER_SLASHINGS
  private final SSZList<AttesterSlashing>
      attester_slashings; // List bounded by MAX_ATTESTER_SLASHINGS
  private final SSZList<Attestation> attestations; // List bounded by MAX_ATTESTATIONS
  private final SSZList<Deposit> deposits; // List bounded by MAX_DEPOSITS
  private final SSZList<SignedVoluntaryExit> voluntary_exits; // List bounded by MAX_VOLUNTARY_EXITS

  @Label("sos-ignore")
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calculateRoot);

  public BeaconBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    this.randao_reveal = randao_reveal;
    this.eth1_data = eth1_data;
    this.graffiti = graffiti;
    this.proposer_slashings = proposer_slashings;
    this.attester_slashings = attester_slashings;
    this.attestations = attestations;
    this.deposits = deposits;
    this.voluntary_exits = voluntary_exits;
  }

  public BeaconBlockBody() {
    this.randao_reveal = BLSSignature.empty();
    this.eth1_data = new Eth1Data();
    this.graffiti = Bytes32.ZERO;
    this.proposer_slashings = BeaconBlockBodyLists.createProposerSlashings();
    this.attester_slashings = BeaconBlockBodyLists.createAttesterSlashings();
    this.attestations = BeaconBlockBodyLists.createAttestations();
    this.deposits = BeaconBlockBodyLists.createDeposits();
    this.voluntary_exits = BeaconBlockBodyLists.createVoluntaryExits();
  }

  @Override
  public int getSSZFieldCount() {
    return randao_reveal.getSSZFieldCount() + eth1_data.getSSZFieldCount() + SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(randao_reveal.get_fixed_parts());
    fixedPartsList.addAll(eth1_data.get_fixed_parts());
    fixedPartsList.addAll(List.of(SSZ.encode(writer -> writer.writeFixedBytes(graffiti))));
    fixedPartsList.addAll(Collections.nCopies(5, Bytes.EMPTY));
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.addAll(Collections.nCopies(randao_reveal.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(Collections.nCopies(eth1_data.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(List.of(Bytes.EMPTY));
    variablePartsList.addAll(
        List.of(
            SimpleOffsetSerializer.serializeFixedCompositeList(proposer_slashings),
            SimpleOffsetSerializer.serializeVariableCompositeList(attester_slashings),
            SimpleOffsetSerializer.serializeVariableCompositeList(attestations),
            SimpleOffsetSerializer.serializeFixedCompositeList(deposits),
            SimpleOffsetSerializer.serializeFixedCompositeList(voluntary_exits)));
    return variablePartsList;
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
        voluntary_exits);
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
        && Objects.equals(this.getVoluntary_exits(), other.getVoluntary_exits());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BLSSignature getRandao_reveal() {
    return randao_reveal;
  }

  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  public Bytes32 getGraffiti() {
    return graffiti;
  }

  public SSZList<Attestation> getAttestations() {
    return attestations;
  }

  public SSZList<ProposerSlashing> getProposer_slashings() {
    return proposer_slashings;
  }

  public SSZList<AttesterSlashing> getAttester_slashings() {
    return attester_slashings;
  }

  public SSZList<Deposit> getDeposits() {
    return deposits;
  }

  public SSZList<SignedVoluntaryExit> getVoluntary_exits() {
    return voluntary_exits;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot.get();
  }

  public Bytes32 calculateRoot() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, randao_reveal.toSSZBytes()),
            eth1_data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, graffiti),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, proposer_slashings),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, attester_slashings),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, attestations),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, deposits),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, voluntary_exits)));
  }
}
