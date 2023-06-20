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

package tech.pegasys.teku.beaconrestapi;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BEACON_BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COUNT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.GRAFFITI;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_PEER_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_PEER_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATUS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.REQUIRE_PREPARED_PROPOSERS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.REQUIRE_PREPARED_PROPOSERS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.REQUIRE_VALIDATOR_REGISTRATIONS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.REQUIRE_VALIDATOR_REGISTRATIONS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.START_PERIOD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.STATUS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SUBCOMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TARGET_PEER_COUNT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TARGET_PEER_COUNT_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TOPICS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidators.StatusParameter;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;

public class BeaconRestApiTypes {
  private static final StringValueTypeDefinition<StatusParameter> STATUS_VALUE =
      DeserializableTypeDefinition.string(StatusParameter.class)
          .formatter(StatusParameter::toString)
          .parser(StatusParameter::valueOf)
          .example("active_ongoing")
          .description("ValidatorStatus string")
          .format("string")
          .build();

  public static final ParameterMetadata<String> PARAMETER_STATE_ID =
      new ParameterMetadata<>(PARAM_STATE_ID, CoreTypes.string(PARAM_STATE_ID_DESCRIPTION, "head"));

  public static final ParameterMetadata<String> PARAMETER_VALIDATOR_ID =
      new ParameterMetadata<>(
          PARAM_VALIDATOR_ID, CoreTypes.string(PARAM_VALIDATOR_DESCRIPTION, "1"));

  public static final ParameterMetadata<String> PARAMETER_BLOCK_ID =
      new ParameterMetadata<>(PARAM_BLOCK_ID, CoreTypes.string(PARAM_BLOCK_ID_DESCRIPTION, "head"));

  public static final ParameterMetadata<Bytes32> BLOCK_ROOT_PARAMETER =
      new ParameterMetadata<>(
          BLOCK_ROOT, BYTES32_TYPE.withDescription("Block root. Hex encoded with 0x prefix."));

  public static final ParameterMetadata<UInt64> SLOT_PARAMETER =
      new ParameterMetadata<>(
          SLOT, CoreTypes.UINT64_TYPE.withDescription("`uint64` value representing slot"));

  public static final ParameterMetadata<UInt64> START_PERIOD_PARAMETER =
      new ParameterMetadata<>(
          START_PERIOD,
          CoreTypes.UINT64_TYPE.withDescription("`uint64` value representing start_period"));

  public static final ParameterMetadata<UInt64> COUNT_PARAMETER =
      new ParameterMetadata<>(
          COUNT, CoreTypes.UINT64_TYPE.withDescription("`uint64` value representing count"));

  public static final ParameterMetadata<UInt64> EPOCH_PARAMETER =
      new ParameterMetadata<>(
          EPOCH, CoreTypes.UINT64_TYPE.withDescription(EPOCH_QUERY_DESCRIPTION));

  public static final ParameterMetadata<UInt64> INDEX_PARAMETER =
      new ParameterMetadata<>(
          INDEX, CoreTypes.UINT64_TYPE.withDescription(COMMITTEE_INDEX_QUERY_DESCRIPTION));

  public static final ParameterMetadata<UInt64> COMMITTEE_INDEX_PARAMETER =
      new ParameterMetadata<>(
          COMMITTEE_INDEX,
          CoreTypes.UINT64_TYPE.withDescription(COMMITTEE_INDEX_QUERY_DESCRIPTION));

  public static final ParameterMetadata<Bytes32> PARENT_ROOT_PARAMETER =
      new ParameterMetadata<>(
          PARENT_ROOT, CoreTypes.BYTES32_TYPE.withDescription("Not currently supported."));

  public static final ParameterMetadata<String> ID_PARAMETER =
      new ParameterMetadata<>(PARAM_ID, STRING_TYPE.withDescription(PARAM_VALIDATOR_DESCRIPTION));

  public static final ParameterMetadata<BLSSignature> RANDAO_PARAMETER =
      new ParameterMetadata<>(
          RestApiConstants.RANDAO_REVEAL,
          SIGNATURE_TYPE.withDescription(
              "`BLSSignature Hex` BLS12-381 signature for the current epoch."));

  public static final ParameterMetadata<Bytes32> GRAFFITI_PARAMETER =
      new ParameterMetadata<>(
          GRAFFITI, CoreTypes.BYTES32_TYPE.withDescription("`Bytes32 Hex` Graffiti."));

  public static final ParameterMetadata<Integer> SYNCING_PARAMETER =
      new ParameterMetadata<>(
          SYNCING_STATUS, CoreTypes.RAW_INTEGER_TYPE.withDescription(SYNCING_STATUS_DESCRIPTION));

  public static final ParameterMetadata<String> PEER_ID_PARAMETER =
      new ParameterMetadata<>(
          PARAM_PEER_ID,
          CoreTypes.string(
              PARAM_PEER_ID_DESCRIPTION, "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"));

  public static final ParameterMetadata<StatusParameter> STATUS_PARAMETER =
      new ParameterMetadata<>(STATUS, STATUS_VALUE.withDescription(PARAM_STATUS_DESCRIPTION));

  public static final ParameterMetadata<Bytes32> ATTESTATION_DATA_ROOT_PARAMETER =
      new ParameterMetadata<>(
          ATTESTATION_DATA_ROOT,
          BYTES32_TYPE.withDescription(
              "`String` HashTreeRoot of AttestationData that validator wants aggregated."));

  public static final ParameterMetadata<Integer> SUBCOMMITTEE_INDEX_PARAMETER =
      new ParameterMetadata<>(
          SUBCOMMITTEE_INDEX,
          RAW_INTEGER_TYPE.withDescription(
              "`Integer` The subcommittee index for which to produce the contribution."));

  public static final ParameterMetadata<Bytes32> BEACON_BLOCK_ROOT_PARAMETER =
      new ParameterMetadata<>(
          BEACON_BLOCK_ROOT,
          BYTES32_TYPE.withDescription(
              "`bytes32` The block root for which to produce the contribution."));

  public static final ParameterMetadata<Integer> TARGET_PEER_COUNT_PARAMETER =
      new ParameterMetadata<>(
          TARGET_PEER_COUNT, INTEGER_TYPE.withDescription(TARGET_PEER_COUNT_DESCRIPTION));

  public static final ParameterMetadata<Boolean> REQUIRE_PREPARED_PROPOSERS_PARAMETER =
      new ParameterMetadata<>(
          REQUIRE_PREPARED_PROPOSERS,
          BOOLEAN_TYPE.withDescription(REQUIRE_PREPARED_PROPOSERS_DESCRIPTION));

  public static final ParameterMetadata<Boolean> REQUIRE_VALIDATOR_REGISTRATIONS_PARAMETER =
      new ParameterMetadata<>(
          REQUIRE_VALIDATOR_REGISTRATIONS,
          BOOLEAN_TYPE.withDescription(REQUIRE_VALIDATOR_REGISTRATIONS_DESCRIPTION));

  public static final ParameterMetadata<String> TOPICS_PARAMETER =
      new ParameterMetadata<>(
          TOPICS,
          CoreTypes.string(
              "Event types to subscribe to."
                  + " Available values include: [`head`, `finalized_checkpoint`, `chain_reorg`, `block`, "
                  + "`attestation`, `voluntary_exit`, `contribution_and_proof`]\n\n",
              "head"));

  public static final SerializableTypeDefinition<Bytes32> ROOT_TYPE =
      SerializableTypeDefinition.object(Bytes32.class)
          .withField("root", BYTES32_TYPE, Function.identity())
          .build();

  public static final SerializableTypeDefinition<BlockAndMetaData> BLOCK_HEADER_TYPE =
      SerializableTypeDefinition.object(BlockAndMetaData.class)
          .withField("root", BYTES32_TYPE, block -> block.getData().getRoot())
          .withField("canonical", BOOLEAN_TYPE, BlockAndMetaData::isCanonical)
          .withField(
              "header",
              SignedBeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition(),
              data -> data.getData().asHeader())
          .build();

  public static final ParameterMetadata<UInt64> BLOB_INDICES_PARAMETER =
      new ParameterMetadata<>(
          "indices",
          CoreTypes.UINT64_TYPE.withDescription(
              "Array of indices for blob sidecars to request for in the specified block. Returns all blob sidecars in the block if not specified."));
}
