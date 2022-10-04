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

package tech.pegasys.teku.validator.api;

import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DoppelgangerDetectionResult {
  private final Object2BooleanMap<UInt64> validators;

  public DoppelgangerDetectionResult(Object2BooleanMap<UInt64> validators) {
    this.validators = validators;
  }

  public boolean validatorIsLive(UInt64 validatorIndex) {
    return this.validators.containsKey(validatorIndex)
        && this.validators.getOrDefault(validatorIndex, false);
  }
}
