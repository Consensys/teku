/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class SingleQueryParameterUtils {

  public static final String INVALID_BYTES32_DATA =
      "Unable to read Bytes32 data from query parameter.";
  public static final String INVALID_BYTES96_DATA =
      "Unable to read Bytes96 data from query parameter.";
  public static final String INVALID_NUMERIC_VALUE =
      "Unable to read a numeric value from query parameter.";
  public static final String NULL_OR_EMPTY_FORMAT = "'%s' cannot be null or empty.";
  public static final String MUST_SPECIFY_ONLY_ONCE = "'%s' must have a single value in the query.";

  /**
   * Checks that a parameter exists, has a single entry, and is not an empty string
   *
   * @param parameterMap
   * @param key
   * @return returns the Value from the key, or throws IllegalArgumentException
   */
  public static String validateQueryParameter(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    if (parameterMap.containsKey(key)) {
      if (parameterMap.get(key).size() != 1) {
        throw new IllegalArgumentException(String.format(MUST_SPECIFY_ONLY_ONCE, key));
      }
      if (!StringUtils.isEmpty(parameterMap.get(key).get(0))) {
        return parameterMap.get(key).get(0);
      }
    }
    throw new IllegalArgumentException(String.format(NULL_OR_EMPTY_FORMAT, key));
  }

  public static int getParameterValueAsInt(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String stringValue = validateQueryParameter(parameterMap, key);
    try {
      return Integer.parseInt(stringValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(INVALID_NUMERIC_VALUE);
    }
  }

  public static UInt64 getParameterValueAsUInt64(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String stringValue = validateQueryParameter(parameterMap, key);
    try {
      return UInt64.valueOf(stringValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(INVALID_NUMERIC_VALUE);
    }
  }

  public static UInt64 getParameterValueAsEpoch(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    final UInt64 value = getParameterValueAsUInt64(parameterMap, key);
    // Restrict valid epoch values to ones that can be converted to slot without overflowing
    if (value.isGreaterThan(UInt64.MAX_VALUE.dividedBy(Constants.SLOTS_PER_EPOCH))) {
      throw new IllegalArgumentException(INVALID_NUMERIC_VALUE);
    }
    return value;
  }

  public static long getParameterValueAsLong(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String stringValue = validateQueryParameter(parameterMap, key);
    try {
      return Long.parseLong(stringValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(INVALID_NUMERIC_VALUE);
    }
  }

  public static Bytes32 getParameterValueAsBytes32(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String stringValue = validateQueryParameter(parameterMap, key);
    try {
      return Bytes32.fromHexString(stringValue);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(INVALID_BYTES32_DATA);
    }
  }

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

  public static Optional<Bytes32> getParameterValueAsBytes32IfPresent(
      final Map<String, List<String>> parameterMap, final String key) {
    return parameterMap.containsKey(key)
        ? Optional.of(SingleQueryParameterUtils.getParameterValueAsBytes32(parameterMap, key))
        : Optional.empty();
  }

  public static Optional<UInt64> getParameterValueAsUInt64IfPresent(
      final Map<String, List<String>> parameterMap, final String key) {
    return parameterMap.containsKey(key)
        ? Optional.of(SingleQueryParameterUtils.getParameterValueAsUInt64(parameterMap, key))
        : Optional.empty();
  }
}
