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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_fork_digest;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.containers.Container5;
import tech.pegasys.teku.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszBytes4;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.util.config.Constants;

public class StatusMessage
    extends Container5<StatusMessage, SszBytes4, SszBytes32, SszUInt64, SszBytes32, SszUInt64>
    implements RpcRequest {

  public static class StatusMessageSchema
      extends ContainerSchema5<
          StatusMessage, SszBytes4, SszBytes32, SszUInt64, SszBytes32, SszUInt64> {

    public StatusMessageSchema() {
      super(
          "StatusMessage",
          namedSchema("forkDigest", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("finalizedRoot", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("finalizedEpoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("headRoot", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("headSlot", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public StatusMessage createFromBackingNode(TreeNode node) {
      return new StatusMessage(this, node);
    }
  }

  public static final StatusMessageSchema SSZ_SCHEMA = new StatusMessageSchema();

  private StatusMessage(StatusMessageSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public StatusMessage(
      Bytes4 forkDigest,
      Bytes32 finalizedRoot,
      UInt64 finalizedEpoch,
      Bytes32 headRoot,
      UInt64 headSlot) {
    super(
        SSZ_SCHEMA,
        SszBytes4.of(forkDigest),
        SszBytes32.of(finalizedRoot),
        SszUInt64.of(finalizedEpoch),
        SszBytes32.of(headRoot),
        SszUInt64.of(headSlot));
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
  public int getMaximumRequestChunks() {
    return 1;
  }
}
