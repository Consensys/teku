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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.ssz.containers.Container5;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.tree.TreeNode;

public final class BeaconBlock
    extends Container5<BeaconBlock, SszUInt64, SszUInt64, SszBytes32, SszBytes32, BeaconBlockBody>
    implements BeaconBlockSummary {

  BeaconBlock(final BeaconBlockSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlock(
      BeaconBlockSchema type,
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    super(
        type,
        SszUInt64.of(slot),
        SszUInt64.of(proposer_index),
        SszBytes32.of(parent_root),
        SszBytes32.of(state_root),
        body);
  }

  public static BeaconBlock fromGenesisState(final Spec spec, final BeaconState genesisState) {
    return fromGenesisState(spec.getGenesisSpec().getSchemaDefinitions(), genesisState);
  }

  public static BeaconBlock fromGenesisState(
      final SchemaDefinitions genesisSchema, final BeaconState genesisState) {
    return new BeaconBlock(
        genesisSchema.getBeaconBlockSchema(),
        UInt64.ZERO,
        UInt64.ZERO,
        Bytes32.ZERO,
        genesisState.hashTreeRoot(),
        genesisSchema.getBeaconBlockBodySchema().createEmpty());
  }

  public BeaconBlock withStateRoot(Bytes32 stateRoot) {
    return new BeaconBlock(
        this.getSchema(), getSlot(), getProposerIndex(), getParentRoot(), stateRoot, getBody());
  }

  @Override
  public BeaconBlockSchema getSchema() {
    return (BeaconBlockSchema) super.getSchema();
  }

  @Override
  public UInt64 getSlot() {
    return getField0().get();
  }

  @Override
  public UInt64 getProposerIndex() {
    return getField1().get();
  }

  @Override
  public Bytes32 getParentRoot() {
    return getField2().get();
  }

  @Override
  public Bytes32 getStateRoot() {
    return getField3().get();
  }

  public BeaconBlockBody getBody() {
    return getField4();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getBody().hashTreeRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return hashTreeRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(this);
  }
}
