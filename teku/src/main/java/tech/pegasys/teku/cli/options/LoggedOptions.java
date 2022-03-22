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

package tech.pegasys.teku.cli.options;

import java.util.Collection;
import java.util.StringJoiner;

public interface LoggedOptions {
  String HIDDEN_OPTION_PLACEHOLDER = "*** HIDDEN ***";

  /**
   * Similar to general toString() where fields containing sensitive data are wrapped with {@link
   * #hideSensitiveOption(Object)}
   *
   * @return String representation of option fields
   */
  String presentOptions();

  default String hideSensitiveOption(final Object object) {
    if (object == null || (object instanceof Collection && ((Collection<?>) object).isEmpty())) {
      return "null";
    } else {
      return HIDDEN_OPTION_PLACEHOLDER;
    }
  }

  static String presentOptionsCollection(final Collection<Object> options) {
    StringJoiner sj = new StringJoiner(",");
    for (Object option : options) {
      if (option instanceof LoggedOptions) {
        sj.add(((LoggedOptions) option).presentOptions());
      }
    }
    return sj.toString();
  }
}
