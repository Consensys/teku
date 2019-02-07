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

package tech.pegasys.artemis.statetransition.util;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.stream.IntStream;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.Validators;

public class ValidatorsUtil {

  public static Validators get_active_validators(Validators validators, UnsignedLong epoch) {
    Validators active_validators = new Validators();
    if (validators != null) {
      for (Validator record : validators) {
        if (record.is_active_validator(epoch)) active_validators.add(record);
      }
    }
    return active_validators;
  }

  public static ArrayList<Integer> get_active_validator_indices(
      Validators validators, UnsignedLong epoch) {
    ArrayList<Integer> active_validator_indices = new ArrayList<>();

    IntStream.range(0, validators.size())
        .forEachOrdered(
            n -> {
              if (validators.get(n).is_active_validator(epoch)) active_validator_indices.add(n);
            });

    return active_validator_indices;
  }

  public static double get_effective_balance(Validators validators) {

    //    return validators != null
    //        ? validators.stream().mapToDouble(Validator::get_effective_balance).sum()
    //        : 0.0d;
    return 0.0d;
  }
}
