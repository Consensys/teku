/*
 * Copyright 2022 ConsenSys AG.
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

import static tech.pegasys.teku.beaconrestapi.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BEACON_BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX_QUERY_DESCRIPTION;
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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.STATUS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SUBCOMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class BeaconRestApiTypes {
  private static final StringValueTypeDefinition<ValidatorStatus> STATUS_VALUE =
      DeserializableTypeDefinition.string(ValidatorStatus.class)
          .formatter(ValidatorStatus::toString)
          .parser(ValidatorStatus::valueOf)
          .example("active_ongoing")
          .description("ValidatorStatus string")
          .format("string")
          .build();

  public static final SerializableTypeDefinition<SubmitDataError> SUBMIT_DATA_ERROR_TYPE =
      SerializableTypeDefinition.object(SubmitDataError.class)
          .name("SubmitDataError")
          .withField("index", UINT64_TYPE, SubmitDataError::getIndex)
          .withField("message", STRING_TYPE, SubmitDataError::getMessage)
          .build();

  public static final ParameterMetadata<String> PARAMETER_STATE_ID =
      new ParameterMetadata<>(PARAM_STATE_ID, CoreTypes.string(PARAM_STATE_ID_DESCRIPTION, "head"));

  public static final ParameterMetadata<String> PARAMETER_VALIDATOR_ID =
      new ParameterMetadata<>(
          PARAM_VALIDATOR_ID, CoreTypes.string(PARAM_VALIDATOR_DESCRIPTION, "1"));

  public static final ParameterMetadata<String> PARAMETER_BLOCK_ID =
      new ParameterMetadata<>(PARAM_BLOCK_ID, CoreTypes.string(PARAM_BLOCK_ID_DESCRIPTION, "head"));

  public static final ParameterMetadata<UInt64> SLOT_PARAMETER =
      new ParameterMetadata<>(
          SLOT, CoreTypes.UINT64_TYPE.withDescription("`uint64` value representing slot"));

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
          SYNCING_STATUS, CoreTypes.INTEGER_TYPE.withDescription(SYNCING_STATUS_DESCRIPTION));

  public static final ParameterMetadata<String> PEER_ID_PARAMETER =
      new ParameterMetadata<>(
          PARAM_PEER_ID,
          CoreTypes.string(
              PARAM_PEER_ID_DESCRIPTION, "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"));

  public static final ParameterMetadata<ValidatorStatus> STATUS_PARAMETER =
      new ParameterMetadata<>(STATUS, STATUS_VALUE.withDescription(PARAM_STATUS_DESCRIPTION));

  public static final ParameterMetadata<Bytes32> ATTESTATION_DATA_ROOT_PARAMETER =
      new ParameterMetadata<>(
          ATTESTATION_DATA_ROOT,
          BYTES32_TYPE.withDescription(
              "`String` HashTreeRoot of AttestationData that validator wants aggregated."));

  public static final ParameterMetadata<Integer> SUBCOMMITTEE_INDEX_PARAMETER =
      new ParameterMetadata<>(
          SUBCOMMITTEE_INDEX,
          INTEGER_TYPE.withDescription(
              "`Integer` The subcommittee index for which to produce the contribution."));

  public static final ParameterMetadata<Bytes32> BEACON_BLOCK_ROOT_PARAMETER =
      new ParameterMetadata<>(
          BEACON_BLOCK_ROOT,
          BYTES32_TYPE.withDescription(
              "`bytes32` The block root for which to produce the contribution."));
}
