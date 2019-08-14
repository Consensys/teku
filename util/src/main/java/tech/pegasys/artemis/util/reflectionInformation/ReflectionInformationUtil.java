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

package tech.pegasys.artemis.util.reflectionInformation;

import java.lang.reflect.Field;
import java.util.List;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;

public class ReflectionInformationUtil {
  @SuppressWarnings("rawtypes")
  public static boolean isVariable(ReflectionInformation reflectionInformation)
      throws SecurityException {
    Field[] fields = reflectionInformation.getFields();
    for (int i = 0; i < fields.length; i++) {
      Class type = fields[i].getType();
      if (type.equals(List.class)) {
        return true;
      }
      if (SSZContainer.class.isAssignableFrom(type)) {
        if (isVariable(new ReflectionInformation(type))) {
          return true;
        }
      }
    }
    return false;
  }
}
