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

package tech.pegasys.teku.api.schema.altair;

import static tech.pegasys.teku.api.schema.altair.BeaconBlockBodyAltair.BEACON_BLOCK_ALTAIR_BODY_TYPE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.interfaces.UnsignedBlock;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

@SuppressWarnings("JavaCase")
public class BeaconBlockAltair extends BeaconBlock implements UnsignedBlock {
  public static final DeserializableTypeDefinition<BeaconBlockAltair> BEACON_BLOCK_ALTAIR_TYPE =
      DeserializableTypeDefinition.object(BeaconBlockAltair.class)
          .initializer(BeaconBlockAltair::new)
          .withField(
              "slot", CoreTypes.UINT64_TYPE, BeaconBlockAltair::getSlot, BeaconBlockAltair::setSlot)
          .withField(
              "proposer_index",
              CoreTypes.UINT64_TYPE,
              BeaconBlockAltair::getProposerIndex,
              BeaconBlockAltair::setProposerIndex)
          .withField(
              "parent_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockAltair::getParentRoot,
              BeaconBlockAltair::setParentRoot)
          .withField(
              "state_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockAltair::getStateRoot,
              BeaconBlockAltair::setStateRoot)
          .withField(
              "body",
              BEACON_BLOCK_ALTAIR_BODY_TYPE,
              BeaconBlockAltair::getBody,
              BeaconBlockAltair::setBody)
          .build();

  public BeaconBlockAltair() {}

  public BeaconBlockAltair(tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock message) {
    super(
        message.getSlot(),
        message.getProposerIndex(),
        message.getParentRoot(),
        message.getStateRoot(),
        new BeaconBlockBodyAltair(message.getBody().toVersionAltair().orElseThrow()));
  }

  @Override
  public tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock asInternalBeaconBlock(
      final Spec spec) {
    final SpecVersion specVersion = spec.atSlot(slot);
    return SchemaDefinitionsAltair.required(specVersion.getSchemaDefinitions())
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
  public BeaconBlockBodyAltair getBody() {
    return (BeaconBlockBodyAltair) body;
  }

  @JsonCreator
  public BeaconBlockAltair(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("proposer_index") final UInt64 proposer_index,
      @JsonProperty("parent_root") final Bytes32 parent_root,
      @JsonProperty("state_root") final Bytes32 state_root,
      @JsonProperty("body") final BeaconBlockBodyAltair body) {
    super(slot, proposer_index, parent_root, state_root, body);
  }
}
