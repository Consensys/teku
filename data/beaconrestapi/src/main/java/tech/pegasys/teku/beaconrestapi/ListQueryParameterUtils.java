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

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class ListQueryParameterUtils {
  public static final String MISSING_QUERY_PARAMETER = "Query parameter %s not found";
  public static final Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();

  private static List<String> validateQueryParameter(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    if (parameterMap.containsKey(key)) {
      final List<String> values = parameterMap.get(key);
      if (values.isEmpty()) {
        throw new IllegalArgumentException(String.format(MISSING_QUERY_PARAMETER, key));
      }
      // if its an array, we should filter any individual empty values out for sanity
      return parameterMap.get(key).stream()
          .filter(StringUtils::isNotEmpty)
          .collect(Collectors.toList());
    }
    throw new IllegalArgumentException(String.format(MISSING_QUERY_PARAMETER, key));
  }

  public static List<Integer> getParameterAsIntegerList(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String integerList = "";
    try {
      integerList = String.join(",", validateQueryParameter(parameterMap, key));
      return splitter
          .splitToStream(integerList)
          .map(Integer::parseUnsignedInt)
          .collect(Collectors.toList());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          String.format("Failed to parse query parameter (%s:%s) to Integers", key, integerList),
          ex);
    }
  }

  public static List<String> getParameterAsStringList(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    final String list = String.join(",", validateQueryParameter(parameterMap, key));
    return splitter.splitToStream(list).distinct().map(String::trim).collect(Collectors.toList());
  }
}
