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

import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.validateQueryParameter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SingleQueryParameterUtils {
  public static final String INVALID_BYTES96_DATA =
      "Unable to read Bytes96 data from query parameter.";

  public static BLSSignature getParameterValueAsBLSSignature(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String stringValue = validateQueryParameter(parameterMap, key);
    try {
      return BLSSignature.fromHexString(stringValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(INVALID_BYTES96_DATA);
    }
  }

  public static Optional<UInt64> getParameterValueAsUInt64IfPresent(
      final Map<String, List<String>> parameterMap, final String key) {
    try {
      return parameterMap.containsKey(key)
          ? Optional.of(
              tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils
                  .getParameterValueAsUInt64(parameterMap, key))
          : Optional.empty();
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException(
          "Invalid value for " + key + ": " + String.join(",", parameterMap.get(key)));
    }
  }
}
