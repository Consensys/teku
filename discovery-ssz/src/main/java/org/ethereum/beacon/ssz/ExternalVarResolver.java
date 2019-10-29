/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.ssz;

import java.util.function.Function;
import org.ethereum.beacon.ssz.annotation.SSZ;

/** Resolves external runtime property value. For example see {@link SSZ#vectorLengthVar()} */
public interface ExternalVarResolver extends Function<String, Object> {

  class ExternalVariableNotDefined extends SSZSchemeException {

    ExternalVariableNotDefined(String s) {
      super(s);
    }
  }

  class ExternalVariableInvalidType extends SSZSchemeException {

    public ExternalVariableInvalidType(String message) {
      super(message);
    }
  }

  static ExternalVarResolver fromFunction(Function<String, Object> function) {
    return function::apply;
  }

  /**
   * Resolves the variable of specific type.
   *
   * @throws SSZSchemeException if variable is absent or of a wrong type
   */
  default <T> T resolveOrThrow(String varName, Class<T> type) {
    Object ret = apply(varName);
    if (ret == null) {
      throw new ExternalVariableNotDefined("Mandatory variable not defined: " + varName);
    }
    if (!type.isInstance(ret)) {
      throw new ExternalVariableInvalidType(
          "Variable value type (" + ret.getClass() + ") is not expected: " + type);
    }
    return (T) ret;
  }
}
