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
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container8;
import tech.pegasys.teku.ssz.backing.containers.ContainerType8;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
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

  private static final ListViewType<ProposerSlashing> PROPOSER_SLASHINGS_TYPE =
      new ListViewType<>(ProposerSlashing.TYPE, Constants.MAX_PROPOSER_SLASHINGS);
  private static final ListViewType<AttesterSlashing> ATTESTER_SLASHINGS_TYPE =
      new ListViewType<>(AttesterSlashing.TYPE, Constants.MAX_ATTESTER_SLASHINGS);
  private static final ListViewType<Attestation> ATTESTATIONS_TYPE =
      new ListViewType<>(Attestation.TYPE, Constants.MAX_ATTESTATIONS);
  private static final ListViewType<Deposit> DEPOSITS_TYPE =
      new ListViewType<>(Deposit.TYPE, Constants.MAX_DEPOSITS);
  private static final ListViewType<SignedVoluntaryExit> VOLUNTARY_EXITS_TYPE =
      new ListViewType<>(SignedVoluntaryExit.TYPE, Constants.MAX_VOLUNTARY_EXITS);

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
          new VectorViewType<>(BasicViewTypes.BYTE_TYPE, 96),
          Eth1Data.TYPE,
          BasicViewTypes.BYTES32_TYPE,
          PROPOSER_SLASHINGS_TYPE,
          ATTESTER_SLASHINGS_TYPE,
          ATTESTATIONS_TYPE,
          DEPOSITS_TYPE,
          VOLUNTARY_EXITS_TYPE);
    }

    @Override
    public BeaconBlockBody createFromBackingNode(TreeNode node) {
      return new BeaconBlockBody(this, node);
    }
  }

  @SszTypeDescriptor public static final BeaconBlockBodyType TYPE = new BeaconBlockBodyType();

  public BeaconBlockBody(BeaconBlockBodyType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    super(
        TYPE,
        ViewUtils.createVectorFromBytes(randao_reveal.toBytesCompressed()),
        eth1_data,
        new Bytes32View(graffiti),
        ViewUtils.toListView(PROPOSER_SLASHINGS_TYPE, proposer_slashings),
        ViewUtils.toListView(ATTESTER_SLASHINGS_TYPE, attester_slashings),
        ViewUtils.toListView(ATTESTATIONS_TYPE, attestations),
        ViewUtils.toListView(DEPOSITS_TYPE, deposits),
        ViewUtils.toListView(VOLUNTARY_EXITS_TYPE, voluntary_exits));
  }

  public BeaconBlockBody() {
    super(TYPE);
  }

  public BLSSignature getRandao_reveal() {
    return BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField0()));
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
