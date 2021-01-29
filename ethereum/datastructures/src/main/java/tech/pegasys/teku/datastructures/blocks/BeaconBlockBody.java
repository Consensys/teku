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

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SpecDependent;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container8;
import tech.pegasys.teku.ssz.backing.containers.ContainerType8;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

/** A Beacon block body */
public class BeaconBlockBody
    extends Container8<
        BeaconBlockBody,
        VectorViewRead<ByteView>,
        Eth1Data,
        Bytes32View,
        ListViewRead<ProposerSlashing>,
        ListViewRead<AttesterSlashing>,
        ListViewRead<Attestation>,
        ListViewRead<Deposit>,
        ListViewRead<SignedVoluntaryExit>>
    implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  public static class BeaconBlockBodyType
      extends ContainerType8<
          BeaconBlockBody,
          VectorViewRead<ByteView>,
          Eth1Data,
          Bytes32View,
          ListViewRead<ProposerSlashing>,
          ListViewRead<AttesterSlashing>,
          ListViewRead<Attestation>,
          ListViewRead<Deposit>,
          ListViewRead<SignedVoluntaryExit>> {

    public BeaconBlockBodyType() {
      super(
          "BeaconBlockBody",
          namedType("randao_reveal", ComplexViewTypes.BYTES_96_TYPE),
          namedType("eth1_data", Eth1Data.TYPE),
          namedType("graffiti", BasicViewTypes.BYTES32_TYPE),
          namedType(
              "proposer_slashings",
              new ListViewType<>(ProposerSlashing.TYPE, Constants.MAX_PROPOSER_SLASHINGS)),
          namedType(
              "attester_slashings",
              new ListViewType<>(AttesterSlashing.TYPE, Constants.MAX_ATTESTER_SLASHINGS)),
          namedType(
              "attestations", new ListViewType<>(Attestation.TYPE, Constants.MAX_ATTESTATIONS)),
          namedType("deposits", new ListViewType<>(Deposit.TYPE, Constants.MAX_DEPOSITS)),
          namedType(
              "voluntary_exits",
              new ListViewType<>(SignedVoluntaryExit.TYPE, Constants.MAX_VOLUNTARY_EXITS)));
    }

    public ListViewType<ProposerSlashing> getProposerSlashingsType() {
      return (ListViewType<ProposerSlashing>) getFieldType3();
    }

    public ListViewType<AttesterSlashing> getAttesterSlashingsType() {
      return (ListViewType<AttesterSlashing>) getFieldType4();
    }

    public ListViewType<Attestation> getAttestationsType() {
      return (ListViewType<Attestation>) getFieldType5();
    }

    public ListViewType<Deposit> getDepositsType() {
      return (ListViewType<Deposit>) getFieldType6();
    }

    public ListViewType<SignedVoluntaryExit> getVoluntaryExitsType() {
      return (ListViewType<SignedVoluntaryExit>) getFieldType7();
    }

    @Override
    public BeaconBlockBody createFromBackingNode(TreeNode node) {
      return new BeaconBlockBody(this, node);
    }
  }

  @SszTypeDescriptor
  public static BeaconBlockBodyType getSszType() {
    return TYPE.get();
  }

  public static final SpecDependent<BeaconBlockBodyType> TYPE =
      SpecDependent.of(BeaconBlockBodyType::new);

  private BLSSignature randaoRevealCache;

  private BeaconBlockBody(BeaconBlockBodyType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated // Use the constructor with type
  public BeaconBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    this(
        TYPE.get(),
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
    this.randaoRevealCache = randao_reveal;
  }

  public BeaconBlockBody(
      BeaconBlockBodyType type,
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    super(
        type,
        ViewUtils.createVectorFromBytes(randao_reveal.toBytesCompressed()),
        eth1_data,
        new Bytes32View(graffiti),
        ViewUtils.toListView(type.getProposerSlashingsType(), proposer_slashings),
        ViewUtils.toListView(type.getAttesterSlashingsType(), attester_slashings),
        ViewUtils.toListView(type.getAttestationsType(), attestations),
        ViewUtils.toListView(type.getDepositsType(), deposits),
        ViewUtils.toListView(type.getVoluntaryExitsType(), voluntary_exits));
    this.randaoRevealCache = randao_reveal;
  }

  public BeaconBlockBody() {
    super(TYPE.get());
  }

  public BLSSignature getRandao_reveal() {
    if (randaoRevealCache == null) {
      randaoRevealCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField0()));
    }
    return randaoRevealCache;
  }

  public Eth1Data getEth1_data() {
    return getField1();
  }

  public Bytes32 getGraffiti() {
    return getField2().get();
  }

  public SSZList<ProposerSlashing> getProposer_slashings() {
    return new SSZBackingList<>(
        ProposerSlashing.class, getField3(), Function.identity(), Function.identity());
  }

  public SSZList<AttesterSlashing> getAttester_slashings() {
    return new SSZBackingList<>(
        AttesterSlashing.class, getField4(), Function.identity(), Function.identity());
  }

  public SSZList<Attestation> getAttestations() {
    return new SSZBackingList<>(
        Attestation.class, getField5(), Function.identity(), Function.identity());
  }

  public SSZList<Deposit> getDeposits() {
    return new SSZBackingList<>(
        Deposit.class, getField6(), Function.identity(), Function.identity());
  }

  public SSZList<SignedVoluntaryExit> getVoluntary_exits() {
    return new SSZBackingList<>(
        SignedVoluntaryExit.class, getField7(), Function.identity(), Function.identity());
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
