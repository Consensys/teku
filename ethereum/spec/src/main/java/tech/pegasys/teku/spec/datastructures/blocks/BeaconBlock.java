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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyPhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.ssz.backing.containers.Container5;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema5;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.util.config.SpecDependent;

public final class BeaconBlock
    extends Container5<BeaconBlock, SszUInt64, SszUInt64, SszBytes32, SszBytes32, BeaconBlockBody>
    implements BeaconBlockSummary {

  public static class BeaconBlockSchema
      extends ContainerSchema5<
          BeaconBlock, SszUInt64, SszUInt64, SszBytes32, SszBytes32, BeaconBlockBody> {

    public BeaconBlockSchema() {
      super(
          "BeaconBlock",
          namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("parent_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(
              "body", SszSchema.as(BeaconBlockBody.class, BeaconBlockBodyPhase0.SSZ_SCHEMA.get())));
    }

    @Override
    public BeaconBlock createFromBackingNode(TreeNode node) {
      return new BeaconBlock(this, node);
    }
  }

  public static BeaconBlockSchema getSszSchema() {
    return SSZ_SCHEMA.get();
  }

  public static final SpecDependent<BeaconBlockSchema> SSZ_SCHEMA =
      SpecDependent.of(BeaconBlockSchema::new);

  private BeaconBlock(BeaconBlockSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated
  public BeaconBlock(
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    this(SSZ_SCHEMA.get(), slot, proposer_index, parent_root, state_root, body);
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
        new SszUInt64(slot),
        new SszUInt64(proposer_index),
        new SszBytes32(parent_root),
        new SszBytes32(state_root),
        body);
  }

  public static BeaconBlock fromGenesisState(final Spec spec, final BeaconState genesisState) {
    return fromGenesisState(spec.getGenesisSpec().getSchemaDefinitions(), genesisState);
  }

  public static BeaconBlock fromGenesisState(
      final SchemaDefinitions genesisSchema, final BeaconState genesisState) {
    return new BeaconBlock(
        SSZ_SCHEMA.get(),
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
