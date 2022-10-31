/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.api.schema.bellatrix.BeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BlindedBlockBellatrix;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

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

  public SignedBeaconBlock getSignedBlindedBeaconBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {

    return new SignedBeaconBlock(
        getBlindedBlock(internalBlock.getMessage()),
        new BLSSignature(internalBlock.getSignature()));
  }

  public BeaconBlock getBeaconBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock block) {
    return getBeaconBlock(block, spec.atSlot(block.getSlot()).getMilestone());
  }

  public BeaconBlock getBlindedBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock block) {
    return getBlindedBlock(block, spec.atSlot(block.getSlot()).getMilestone());
  }

  public BeaconBlock getBlindedBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock block,
      final SpecMilestone milestone) {
    switch (milestone) {
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
      case BELLATRIX:
      case CAPELLA: // TODO
        return new BlindedBlockBellatrix(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            getBlindedBlockBodyBellatrix(block.getBody()));
      default:
        throw new IllegalArgumentException("Unsupported milestone for slot " + block.getSlot());
    }
  }

  public BeaconBlock getBeaconBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock block,
      final SpecMilestone milestone) {
    switch (milestone) {
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
      case BELLATRIX:
      case CAPELLA: // TODO
        return new BeaconBlockBellatrix(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            getBeaconBlockBodyBellatrix(block.getBody()));
      default:
        throw new IllegalArgumentException("Unsupported milestone for slot " + block.getSlot());
    }
  }

  private BeaconBlockBodyAltair getBeaconBlockBodyAltair(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyAltair(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair
            .required(body));
  }

  private BeaconBlockBodyBellatrix getBeaconBlockBodyBellatrix(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyBellatrix(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix
            .BeaconBlockBodyBellatrix.required(body));
  }

  private BlindedBeaconBlockBodyBellatrix getBlindedBlockBodyBellatrix(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BlindedBeaconBlockBodyBellatrix(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix
            .BlindedBeaconBlockBodyBellatrix.required(body));
  }

  public BeaconState getBeaconState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final UInt64 slot = state.getSlot();
    switch (spec.atSlot(slot).getMilestone()) {
      case PHASE0:
        return new BeaconStatePhase0(state);
      case ALTAIR:
        return new BeaconStateAltair(state);
      case BELLATRIX:
        return new BeaconStateBellatrix(state);
      default:
        throw new IllegalArgumentException("Unsupported milestone for slot " + slot);
    }
  }
}
