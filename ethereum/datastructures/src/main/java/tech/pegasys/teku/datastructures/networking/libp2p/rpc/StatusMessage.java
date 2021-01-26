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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody.BeaconBlockBodyType;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container5;
import tech.pegasys.teku.ssz.backing.containers.ContainerType5;
import tech.pegasys.teku.ssz.backing.containers.ContainerType8;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes4View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class StatusMessage extends
    Container5<StatusMessage, Bytes4View, Bytes32View, UInt64View, Bytes32View, UInt64View> implements
    RpcRequest, SimpleOffsetSerializable, SSZContainer {

  public static class StatusMessageType
      extends ContainerType5<StatusMessage, Bytes4View, Bytes32View, UInt64View, Bytes32View, UInt64View> {

    public StatusMessageType() {
      super(
          BasicViewTypes.BYTES4_TYPE,
          BasicViewTypes.BYTES32_TYPE,
          BasicViewTypes.UINT64_TYPE,
          BasicViewTypes.BYTES32_TYPE,
          BasicViewTypes.UINT64_TYPE);
    }

    @Override
    public StatusMessage createFromBackingNode(TreeNode node) {
      return new StatusMessage(this, node);
    }
  }

  @SszTypeDescriptor
  public static final StatusMessageType TYPE = new StatusMessageType();

  private StatusMessage(
      ContainerType5<StatusMessage, Bytes4View, Bytes32View, UInt64View, Bytes32View, UInt64View> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public StatusMessage(
      Bytes4 forkDigest,
      Bytes32 finalizedRoot,
      UInt64 finalizedEpoch,
      Bytes32 headRoot,
      UInt64 headSlot) {
    super(TYPE, new Bytes4View(forkDigest), new Bytes32View(finalizedRoot),
        new UInt64View(finalizedEpoch), new Bytes32View(headRoot), new UInt64View(headSlot));
  }

  public static StatusMessage createPreGenesisStatus() {
    return new StatusMessage(
        createPreGenesisForkDigest(), Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, UInt64.ZERO);
  }

  private static Bytes4 createPreGenesisForkDigest() {
    final Bytes4 genesisFork = Constants.GENESIS_FORK_VERSION;
    final Bytes32 emptyValidatorsRoot = Bytes32.ZERO;
    return compute_fork_digest(genesisFork, emptyValidatorsRoot);
  }

  public Bytes4 getForkDigest() {
    return getField0().get();
  }

  public Bytes32 getFinalizedRoot() {
    return getField1().get();
  }

  public UInt64 getFinalizedEpoch() {
    return getField2().get();
  }

  public Bytes32 getHeadRoot() {
    return getField3().get();
  }

  public UInt64 getHeadSlot() {
    return getField4().get();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkDigest", getForkDigest())
        .add("finalizedRoot", getFinalizedRoot())
        .add("finalizedEpoch", getFinalizedEpoch())
        .add("headRoot", getHeadRoot())
        .add("headSlot", getHeadSlot())
        .toString();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}
