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

import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;

import java.util.stream.Collectors;

public class ValidatorsUtil {

    public static Validators get_active_validator_indices(Validators validators) {

        return validators!= null ?
                new Validators(validators.stream()
                        .filter(validatorRecord -> validatorRecord.is_active_validator())
                        .collect(Collectors.toList())) : new Validators();

    }

    public static double get_effective_balance(Validators validators){

        return validators!= null ?
                validators.stream().mapToDouble(ValidatorRecord::get_effective_balance).sum() : 0.0d;

    }
}
