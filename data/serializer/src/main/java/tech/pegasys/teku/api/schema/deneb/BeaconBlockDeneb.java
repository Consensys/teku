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

package tech.pegasys.teku.api.schema.deneb;

import static tech.pegasys.teku.api.schema.deneb.BeaconBlockBodyDeneb.BEACON_BLOCK_DENEB_BODY_TYPE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.altair.BeaconBlockAltair;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BeaconBlockDeneb extends BeaconBlockAltair {

  public static final DeserializableTypeDefinition<BeaconBlockDeneb> BEACON_BLOCK_DENEB_TYPE =
      DeserializableTypeDefinition.object(BeaconBlockDeneb.class)
          .initializer(BeaconBlockDeneb::new)
          .withField(
              "slot", CoreTypes.UINT64_TYPE, BeaconBlockDeneb::getSlot, BeaconBlockDeneb::setSlot)
          .withField(
              "proposer_index",
              CoreTypes.UINT64_TYPE,
              BeaconBlockDeneb::getProposerIndex,
              BeaconBlockDeneb::setProposerIndex)
          .withField(
              "parent_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockDeneb::getParentRoot,
              BeaconBlockDeneb::setParentRoot)
          .withField(
              "state_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockDeneb::getStateRoot,
              BeaconBlockDeneb::setStateRoot)
          .withField(
              "body",
              BEACON_BLOCK_DENEB_BODY_TYPE,
              BeaconBlockDeneb::getBody,
              BeaconBlockDeneb::setBody)
          .build();

  public BeaconBlockDeneb(final BeaconBlock message) {
    super(
        message.getSlot(),
        message.getProposerIndex(),
        message.getParentRoot(),
        message.getStateRoot(),
        new BeaconBlockBodyDeneb(message.getBody().toVersionDeneb().orElseThrow()));
  }

  public BeaconBlockDeneb() {}

  @Override
  public BeaconBlock asInternalBeaconBlock(final Spec spec) {
    final SpecVersion specVersion = spec.atSlot(slot);
    return SchemaDefinitionsDeneb.required(specVersion.getSchemaDefinitions())
        .getBeaconBlockSchema()
        .create(
            slot,
            proposer_index,
            parent_root,
            state_root,
            body.asInternalBeaconBlockBody(specVersion));
  }

  @JsonProperty("body")
  @Override
  public BeaconBlockBodyDeneb getBody() {
    return (BeaconBlockBodyDeneb) body;
  }

  @JsonCreator
  public BeaconBlockDeneb(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("proposer_index") final UInt64 proposerIndex,
      @JsonProperty("parent_root") final Bytes32 parentRoot,
      @JsonProperty("state_root") final Bytes32 stateRoot,
      @JsonProperty("body") final BeaconBlockBodyDeneb body) {
    super(slot, proposerIndex, parentRoot, stateRoot, body);
  }
}
