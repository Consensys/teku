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

package tech.pegasys.artemis.datastructures.util;

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;

public class ValidatorsUtil {

  /**
   * Returns the list of active validators from the provided list of validators at the given epoch.
   *
   * <p><b>This method is defined for convenience and is not mentioned in the spec.</b>
   *
   * @param validators - The list of validators under consideration.
   * @param epoch - The epoch under consideration.
   * @return A list of active validators for the given epoch.
   */
  public static List<Validator> get_active_validators(
      List<Validator> validators, UnsignedLong epoch) {
    List<Validator> active_validators = new ArrayList<>();
    if (validators != null) {
      for (Validator record : validators) {
        if (record.is_active_validator(epoch)) {
          active_validators.add(record);
        }
      }
    }
    return active_validators;
  }

  /**
   * Returns the list of indices of active validators from the provided list of validators at the
   * given epoch.
   *
   * @param validators - The list of validators under consideration.
   * @param epoch - The epoch under consideration.
   * @return A list of indices representing the active validators for the given epoch.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_active_validator_indices">get_active_validator_indices
   *     - Spec v0.4</a>
   */
  public static List<Integer> get_active_validator_indices(
      List<Validator> validators, UnsignedLong epoch) {
    List<Integer> active_validator_indices = Collections.synchronizedList(new ArrayList<Integer>());
    IntStream.range(0, validators.size())
        .parallel()
        .forEachOrdered(
            index -> {
              if (validators.get(index).is_active_validator(epoch)) {
                active_validator_indices.add(index);
              }
            });

    return active_validator_indices;
  }

  /**
   * if index represents an active validator then return True else return False
   *
   * @param state
   * @param index
   * @param epoch
   * @return
   */
  public static Boolean is_active_validator_index(
      BeaconState state, int index, UnsignedLong epoch) {
    List<Validator> all_validators = state.getValidator_registry();
    return all_validators.get(index).is_active_validator(epoch);
  }

  /**
   * find all validators not present in the provided list
   *
   * @param validator_indices
   * @return
   */
  public static List<Integer> get_validators_not_present(List<Integer> validator_indices) {
    List<Integer> all_indices =
        IntStream.range(0, validator_indices.size()).boxed().collect(Collectors.toList());
    Set<Integer> set_of_indices = Sets.newHashSet(all_indices);
    Set<Integer> set_of_validator_indices = Sets.newHashSet(validator_indices);
    // remove all validator indices provided and we are left with validator indices not present in
    // the list provided
    set_of_indices.removeAll(set_of_validator_indices);
    return new ArrayList<>(set_of_indices);
  }
}
