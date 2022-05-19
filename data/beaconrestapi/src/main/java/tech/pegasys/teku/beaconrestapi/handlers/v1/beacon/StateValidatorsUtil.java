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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;

public class StateValidatorsUtil {

  public Set<ValidatorStatus> parseStatusFilter(final List<String> statusList) {
    try {
      return statusList.stream().map(ValidatorStatus::valueOf).collect(Collectors.toSet());
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException("Invalid validator state requested: " + ex.getMessage());
    }
  }
}
