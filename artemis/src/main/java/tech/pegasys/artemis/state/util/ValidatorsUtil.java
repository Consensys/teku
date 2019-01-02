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

import java.util.Iterator;

public class ValidatorsUtil {

    public static Validators get_active_validator_indices(Validators validators) {
        Validators active_validator_indices = new Validators();
        if(validators != null) {
            Iterator<ValidatorRecord> itr = validators.iterator();
            while (itr.hasNext()) {
                ValidatorRecord record = itr.next();
                if (record.is_active_validator()) active_validator_indices.add(record);
            }
        }
        return active_validator_indices;
    }

    public static double get_effective_balance(Validators validators){
        double effective_balance = 0.0d;
        if(validators != null) {
            Iterator<ValidatorRecord> itr = validators.iterator();
            while (itr.hasNext()) {
                ValidatorRecord record = itr.next();
                effective_balance += record.get_effective_balance();
            }
        }
        return effective_balance;
    }
}
