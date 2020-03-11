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

import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.NULL_OR_EMPTY_FORMAT;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class ListQueryParameterUtils {
  public static final String MUST_SPECIFY = "'%s' must have a value in the query.";

  /**
   * Checks that a parameter exists, is not an empty string, and returns list of values
   *
   * @param parameterMap
   * @param key
   * @return returns the Value from the key, or throws IllegalArgumentException
   */
  public static List<String> validateListQueryParameter(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    if (parameterMap.containsKey(key)) {
      if (parameterMap.get(key).size() == 0) {
        throw new IllegalArgumentException(String.format(MUST_SPECIFY, key));
      }
      if (!StringUtils.isEmpty(parameterMap.get(key).get(0))) {
        return parameterMap.get(key);
      }
    }
    throw new IllegalArgumentException(String.format(NULL_OR_EMPTY_FORMAT, key));
  }
}
