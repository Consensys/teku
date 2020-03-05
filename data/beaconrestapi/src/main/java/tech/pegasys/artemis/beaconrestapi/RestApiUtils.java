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

package tech.pegasys.artemis.beaconrestapi;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class RestApiUtils {

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
    if (parameterMap.containsKey(key)
        && parameterMap.get(key).size() == 1
        && !StringUtils.isEmpty(parameterMap.get(key).get(0))) {
      return parameterMap.get(key).get(0);
    }
    throw new IllegalArgumentException(String.format("'%s' cannot be null or empty.", key));
  }

  public static int getParameterValueAsInt(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    String stringValue = validateQueryParameter(parameterMap, key);
    return Integer.valueOf(stringValue);
  }
}
