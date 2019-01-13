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

package tech.pegasys.artemis.state.util;

import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;

public class ValidatorsUtil {

  public static Validators get_active_validators(Validators validators) {
    Validators active_validators = new Validators();
    if (validators != null) {
      for (ValidatorRecord record : validators) {
        if (record.is_active_validator()) active_validators.add(record);
      }
    }
    return active_validators;
  }

  /**
   * Gets indices of active validators from ``validators``.
   *
   * @param validators
   * @return
   */
  public static ArrayList<Integer> get_active_validator_indices(
      ArrayList<ValidatorRecord> validators) {
    ArrayList<Integer> active_validator_indices = new ArrayList<>();
    for (int i = 0; i < validators.size(); i++) {
      if (validators.get(i).getStatus().equals(UnsignedLong.valueOf(ACTIVE))
          || validators.get(i).getStatus().equals(UnsignedLong.valueOf(ACTIVE_PENDING_EXIT))) {
        active_validator_indices.add(i);
      }
    }
    return active_validator_indices;
  }

  public static double get_effective_balance(Validators validators) {
    return validators != null
        ? validators.stream().mapToDouble(ValidatorRecord::get_effective_balance).sum()
        : 0.0d;
  }
}
