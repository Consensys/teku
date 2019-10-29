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

package org.ethereum.beacon.core.spec;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

public class SpecConstantsResolver implements Function<String, Object> {
  private static final String SPEC_CONST_PREFIX = "spec.";

  private final SpecConstants constants;
  private final Converter<String, String> caseConverter;
  private final Map<String, Method> constMethods = new HashMap<>();

  public SpecConstantsResolver(SpecConstants constants) {
    this.constants = constants;
    caseConverter = CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL);
    for (Method method : SpecConstants.class.getMethods()) {
      if (method.getParameterTypes().length == 0
          && method.getName().startsWith("get")
          && isNumber(method.getReturnType())) {
        constMethods.put(method.getName(), method);
      }
    }
  }

  private boolean isNumber(Class type) {
    return type == int.class
        || type == long.class
        || type == byte.class
        || Number.class.isAssignableFrom(
            type); // XXX: `isAssignable` is eligible only for non-primitives
  }

  @Override
  public Object apply(String varName) {
    return resolveByName(varName).orElse(null);
  }

  /**
   * Name should be in the original spec notation (upper underscore), like TARGET_COMMITTEE_SIZE
   *
   * @return <code>empty</code> if the constant not found
   */
  public Optional<Number> resolveByName(@Nonnull String varName) {
    if (varName.startsWith(SPEC_CONST_PREFIX)) {
      String constName = varName.substring(SPEC_CONST_PREFIX.length());
      String convertedName = caseConverter.convert(constName);
      Method getter = constMethods.get("get" + convertedName);
      if (getter == null) {
        return Optional.empty();
      }
      try {
        return Optional.of((Number) getter.invoke(constants));
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException("Couldn't retrieve constant " + constName, e);
      }
    } else {
      return Optional.empty();
    }
  }
}
