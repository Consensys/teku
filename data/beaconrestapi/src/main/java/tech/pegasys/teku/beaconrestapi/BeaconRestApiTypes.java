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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATUS_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_VALIDATOR_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.STATUS;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconRestApiTypes {
  private static final StringValueTypeDefinition<ValidatorStatus> STATUS_VALUE =
      DeserializableTypeDefinition.string(ValidatorStatus.class)
          .formatter(ValidatorStatus::toString)
          .parser(ValidatorStatus::valueOf)
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

  public static final ParameterMetadata<UInt64> SLOT_PARAMETER =
      new ParameterMetadata<>(SLOT, CoreTypes.UINT64_TYPE.withDescription(SLOT_QUERY_DESCRIPTION));

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
      new ParameterMetadata<>(
          PARAM_ID, CoreTypes.STRING_TYPE.withDescription(PARAM_VALIDATOR_DESCRIPTION));

  public static final ParameterMetadata<ValidatorStatus> STATUS_PARAMETER =
      new ParameterMetadata<>(STATUS, STATUS_VALUE.withDescription(PARAM_STATUS_DESCRIPTION));
}
