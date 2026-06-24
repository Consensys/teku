/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v2.debug;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ForkChoiceDataV2;
import tech.pegasys.teku.api.ForkChoiceNodeDataV2;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.EnumTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class GetForkChoiceV2 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v2/debug/fork_choice";

  private static final SerializableTypeDefinition<Map<String, String>> NODE_EXTRA_DATA_TYPE =
      DeserializableTypeDefinition.mapOfStrings();

  private static final SerializableTypeDefinition<ForkChoicePayloadStatus> PAYLOAD_STATUS_TYPE =
      new EnumTypeDefinition.EnumTypeBuilder<>(
              ForkChoicePayloadStatus.class, GetForkChoiceV2::payloadStatusToString)
          .build();

  private static final SerializableTypeDefinition<List<ForkChoiceNodeDataV2>> NODES_TYPE =
      SerializableTypeDefinition.listOf(
          SerializableTypeDefinition.object(ForkChoiceNodeDataV2.class)
              .withField(
                  "payload_status",
                  PAYLOAD_STATUS_TYPE.withDescription(
                      "The fork choice payload status of this node."),
                  ForkChoiceNodeDataV2::getPayloadStatus)
              .withField(
                  "slot",
                  UINT64_TYPE.withDescription("The slot to which this block corresponds."),
                  node -> node.getNode().getSlot())
              .withField(
                  "block_root",
                  BYTES32_TYPE.withDescription("The signing merkle root of the `BeaconBlock`."),
                  node -> node.getNode().getRoot())
              .withField(
                  "parent_root",
                  BYTES32_TYPE.withDescription(
                      "The signing merkle root of the parent `BeaconBlock`."),
                  node -> node.getNode().getParentRoot())
              .withField("weight", UINT64_TYPE, node -> node.getNode().getWeight())
              .withField(
                  "validity",
                  DeserializableTypeDefinition.enumOf(ProtoNodeValidationStatus.class, true),
                  node -> node.getNode().getValidationStatus())
              .withField(
                  "execution_block_hash",
                  BYTES32_TYPE.withDescription(
                      "The `block_hash` from the `execution_payload` of the `BeaconBlock`"),
                  node -> node.getNode().getExecutionBlockHash())
              .withField(
                  "payload_attester_count",
                  UINT64_TYPE.withDescription(
                      "Number of PTC (Payload Timeliness Committee) members that voted for this payload."),
                  ForkChoiceNodeDataV2::getPayloadAttesterCount)
              .withField(
                  "payload_availability_yes_count",
                  UINT64_TYPE.withDescription(
                      "Number of PTC members that voted the payload as available."),
                  ForkChoiceNodeDataV2::getPayloadAvailabilityYesCount)
              .withField(
                  "payload_data_availability_yes_count",
                  UINT64_TYPE.withDescription(
                      "Number of PTC members that voted the payload's data as available."),
                  ForkChoiceNodeDataV2::getPayloadDataAvailabilityYesCount)
              .withField(
                  "state_root",
                  BYTES32_TYPE.withDescription("The signing merkle root of the `BeaconState`."),
                  node -> node.getNode().getStateRoot())
              .withField(
                  "justified_root",
                  BYTES32_TYPE.withDescription("The root of the justified checkpoint."),
                  node -> node.getNode().getCheckpoints().getJustifiedCheckpoint().getRoot())
              .withField(
                  "unrealised_justified_epoch",
                  UINT64_TYPE.withDescription("The epoch of the unrealized justified checkpoint."),
                  node ->
                      node.getNode().getCheckpoints().getUnrealizedJustifiedCheckpoint().getEpoch())
              .withField(
                  "unrealized_justified_root",
                  BYTES32_TYPE.withDescription("The root of the unrealized justified checkpoint."),
                  node ->
                      node.getNode().getCheckpoints().getUnrealizedJustifiedCheckpoint().getRoot())
              .withField(
                  "unrealised_finalized_epoch",
                  UINT64_TYPE.withDescription("The epoch of the unrealized finalized checkpoint."),
                  node ->
                      node.getNode().getCheckpoints().getUnrealizedFinalizedCheckpoint().getEpoch())
              .withField(
                  "unrealized_finalized_root",
                  BYTES32_TYPE.withDescription("The root of the unrealized finalized checkpoint."),
                  node ->
                      node.getNode().getCheckpoints().getUnrealizedFinalizedCheckpoint().getRoot())
              .withOptionalField(
                  "extra_data", NODE_EXTRA_DATA_TYPE, __ -> Optional.of(Collections.emptyMap()))
              .build());

  private static final SerializableTypeDefinition<ForkChoiceDataV2> DATA_TYPE =
      SerializableTypeDefinition.object(ForkChoiceDataV2.class)
          .withField(
              "justified_checkpoint",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              ForkChoiceDataV2::getJustifiedCheckpoint)
          .withField(
              "finalized_checkpoint",
              Checkpoint.SSZ_SCHEMA.getJsonTypeDefinition(),
              ForkChoiceDataV2::getFinalizedCheckpoint)
          .withField("fork_choice_nodes", NODES_TYPE, ForkChoiceDataV2::getNodes)
          .withField(
              "extra_data",
              DeserializableTypeDefinition.mapOfStrings(),
              __ -> Collections.emptyMap())
          .build();

  private static final SerializableTypeDefinition<ForkChoiceDataV2> RESPONSE_TYPE =
      SerializableTypeDefinition.object(ForkChoiceDataV2.class)
          .name("GetForkChoiceResponseV2")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  private final ChainDataProvider chainDataProvider;

  public GetForkChoiceV2(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetForkChoiceV2(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDebugForkChoiceV2")
            .summary("Get fork choice array (V2)")
            .description("Retrieves all current fork choice context.")
            .tags(TAG_DEBUG)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(chainDataProvider.getForkChoiceDataV2());
  }

  private static String payloadStatusToString(final ForkChoicePayloadStatus payloadStatus) {
    return switch (payloadStatus) {
      case PAYLOAD_STATUS_EMPTY -> "empty";
      case PAYLOAD_STATUS_FULL -> "full";
      case PAYLOAD_STATUS_PENDING -> "pending";
    };
  }
}
