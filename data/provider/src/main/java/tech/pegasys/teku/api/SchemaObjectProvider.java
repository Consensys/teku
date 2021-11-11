/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.api;

import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.BeaconBlockBody;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.altair.BeaconBlockAltair;
import tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.api.schema.merge.BeaconBlockMerge;
import tech.pegasys.teku.api.schema.merge.BeaconStateMerge;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

/**
 * Takes objects from Internal layers and converts to an appropriate schema object.
 *
 * <p>Handles slot sensitive conversions like conversion of blocks to phase0 or altair blocks
 */
public class SchemaObjectProvider {
  final Spec spec;

  public SchemaObjectProvider(final Spec spec) {
    this.spec = spec;
  }

  public SignedBeaconBlock getSignedBeaconBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {

    return new SignedBeaconBlock(
        getBeaconBlock(internalBlock.getMessage()), new BLSSignature(internalBlock.getSignature()));
  }

  public BeaconBlock getBeaconBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock block) {
    final UInt64 slot = block.getSlot();
    switch (spec.atSlot(slot).getMilestone()) {
      case PHASE0:
        return new BeaconBlockPhase0(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            new BeaconBlockBody(block.getBody()));
      case ALTAIR:
        return new BeaconBlockAltair(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            getBeaconBlockBodyAltair(block.getBody()));
      case MERGE:
        return new BeaconBlockMerge(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            getBeaconBlockBodyMerge(block.getBody()));
      default:
        throw new IllegalArgumentException("Unsupported milestone for slot " + slot);
    }
  }

  private BeaconBlockBodyAltair getBeaconBlockBodyAltair(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyAltair(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair
            .required(body));
  }

  private BeaconBlockBodyMerge getBeaconBlockBodyMerge(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyMerge(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge
            .required(body));
  }

  public BeaconState getBeaconState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final UInt64 slot = state.getSlot();
    switch (spec.atSlot(slot).getMilestone()) {
      case PHASE0:
        return new BeaconStatePhase0(state);
      case ALTAIR:
        return new BeaconStateAltair(state);
      case MERGE:
        return new BeaconStateMerge(state);
      default:
        throw new IllegalArgumentException("Unsupported milestone for slot " + slot);
    }
  }
}
