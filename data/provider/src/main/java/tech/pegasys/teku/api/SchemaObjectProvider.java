/*
 * Copyright Consensys Software Inc., 2022
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

import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.BeaconBlockBody;
import tech.pegasys.teku.api.schema.altair.BeaconBlockAltair;
import tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.api.schema.bellatrix.BeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.BlindedBlockBellatrix;
import tech.pegasys.teku.api.schema.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.api.schema.capella.BeaconBlockCapella;
import tech.pegasys.teku.api.schema.capella.BlindedBeaconBlockBodyCapella;
import tech.pegasys.teku.api.schema.capella.BlindedBlockCapella;
import tech.pegasys.teku.api.schema.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.api.schema.deneb.BeaconBlockDeneb;
import tech.pegasys.teku.api.schema.deneb.BlindedBeaconBlockBodyDeneb;
import tech.pegasys.teku.api.schema.deneb.BlindedBlockDeneb;
import tech.pegasys.teku.api.schema.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.api.schema.electra.BeaconBlockElectra;
import tech.pegasys.teku.api.schema.electra.BlindedBeaconBlockBodyElectra;
import tech.pegasys.teku.api.schema.electra.BlindedBlockElectra;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
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
    return switch (milestone) {
      case PHASE0 ->
          new BeaconBlockPhase0(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              new BeaconBlockBody(block.getBody()));
      case ALTAIR ->
          new BeaconBlockAltair(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBeaconBlockBodyAltair(block.getBody()));
      case BELLATRIX ->
          new BlindedBlockBellatrix(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBlindedBlockBodyBellatrix(block.getBody()));
      case CAPELLA ->
          new BlindedBlockCapella(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBlindedBlockBodyCapella(block.getBody()));
      case DENEB ->
          new BlindedBlockDeneb(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBlindedBlockBodyDeneb(block.getBody()));
      // TODO update for EIP7805
      case ELECTRA, EIP7805 ->
          new BlindedBlockElectra(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBlindedBlockBodyElectra(block.getBody()));
    };
  }

  public BeaconBlock getBeaconBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock block,
      final SpecMilestone milestone) {
    return switch (milestone) {
      case PHASE0 ->
          new BeaconBlockPhase0(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              new BeaconBlockBody(block.getBody()));
      case ALTAIR ->
          new BeaconBlockAltair(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBeaconBlockBodyAltair(block.getBody()));
      case BELLATRIX ->
          new BeaconBlockBellatrix(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBeaconBlockBodyBellatrix(block.getBody()));
      case CAPELLA ->
          new BeaconBlockCapella(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBeaconBlockBodyCapella(block.getBody()));
      case DENEB ->
          new BeaconBlockDeneb(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBeaconBlockBodyDeneb(block.getBody()));
      // TODO update for EIP7805
      case ELECTRA, EIP7805 ->
          new BeaconBlockElectra(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              block.getStateRoot(),
              getBeaconBlockBodyElectra(block.getBody()));
    };
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

  private BeaconBlockBodyCapella getBeaconBlockBodyCapella(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyCapella(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella
            .BeaconBlockBodyCapella.required(body));
  }

  private BeaconBlockBodyDeneb getBeaconBlockBodyDeneb(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyDeneb(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb
            .required(body));
  }

  private BeaconBlockBodyElectra getBeaconBlockBodyElectra(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BeaconBlockBodyElectra(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra
            .BeaconBlockBodyElectra.required(body));
  }

  private BlindedBeaconBlockBodyBellatrix getBlindedBlockBodyBellatrix(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BlindedBeaconBlockBodyBellatrix(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix
            .BlindedBeaconBlockBodyBellatrix.required(body));
  }

  private BlindedBeaconBlockBodyCapella getBlindedBlockBodyCapella(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BlindedBeaconBlockBodyCapella(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella
            .BlindedBeaconBlockBodyCapella.required(body));
  }

  private BlindedBeaconBlockBodyDeneb getBlindedBlockBodyDeneb(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BlindedBeaconBlockBodyDeneb(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb
            .BlindedBeaconBlockBodyDeneb.required(body));
  }

  private BlindedBeaconBlockBodyElectra getBlindedBlockBodyElectra(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody body) {
    return new BlindedBeaconBlockBodyElectra(
        tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra
            .BlindedBeaconBlockBodyElectra.required(body));
  }
}
