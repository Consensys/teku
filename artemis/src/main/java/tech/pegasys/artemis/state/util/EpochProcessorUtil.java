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

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;
import tech.pegasys.artemis.state.BeaconState;

import java.util.ArrayList;
import java.util.Iterator;

public class EpochProcessorUtil {

    // epoch processing
    public static void updateJustification(BeaconState state){

    }
    public static void updateFinalization(BeaconState state){

    }

    public static void updateCrosslinks(BeaconState state){

    }

    public static void finalBookKeeping(BeaconState state){

    }

    public static void updateValidatorRegistry(BeaconState state){
        Validators active_validator_indices = ValidatorsUtil.get_active_validator_indices(state.getValidator_registry());
        double total_balance = ValidatorsUtil.get_effective_balance(active_validator_indices);

        double max_balance_churn = Math.max((double)(Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH), total_balance/(2*Constants.MAX_BALANCE_CHURN_QUOTIENT));

        updatePendingValidators(max_balance_churn, state);
        updateActivePendingExit(max_balance_churn, state);

        int period_index = Math.toIntExact(state.getSlot() / Constants.COLLECTIVE_PENALTY_CALCULATION_PERIOD);
        ArrayList<Double> latest_penalized_exit_balances = state.getLatest_penalized_exit_balances();

        double total_penalties = latest_penalized_exit_balances.get(period_index) + latest_penalized_exit_balances.get(period_index - 1 < 0 ? period_index - 1 : 0) + latest_penalized_exit_balances.get(period_index - 2 < 0 ? period_index - 2 : 0);

        ArrayList<ValidatorRecord> to_penalize = to_penalize(active_validator_indices);

    }

    private static void updatePendingValidators(double max_balance_churn, BeaconState state){
        double balance_churn = 0.0d;
        Iterator<ValidatorRecord> itr = state.getValidator_registry().iterator();

        while(itr.hasNext()){
            ValidatorRecord validator = itr.next();
            if(validator.getStatus().getValue() == Constants.PENDING_ACTIVATION && validator.getBalance() >= Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH){
                balance_churn += validator.get_effective_balance();

                if(balance_churn > max_balance_churn) break;

                //temporary hack to pass by index to already in place code
                //Java should pass by reference
                state.update_validator_status(state, state.getValidator_registry().indexOf(validator), Constants.ACTIVE);
            }
        }
    }

    private static void updateActivePendingExit(double max_balance_churn, BeaconState state){
        double balance_churn = 0.0d;
        Iterator<ValidatorRecord> itr = state.getValidator_registry().iterator();

        while(itr.hasNext()){
            ValidatorRecord validator = itr.next();
            if(validator.getStatus().getValue() == Constants.ACTIVE_PENDING_EXIT && validator.getBalance() >= Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH){
                balance_churn += validator.get_effective_balance();

                if(balance_churn > max_balance_churn) break;

                //temporary hack to pass by index to already in place code
                //Java should pass by reference
                state.update_validator_status(state, state.getValidator_registry().indexOf(validator), Constants.EXITED_WITHOUT_PENALTY);
            }
        }
    }

    private static void process_ejections(BeaconState state){
        Iterator<ValidatorRecord> itr = state.getValidator_registry().iterator();
        while(itr.hasNext()) {
            ValidatorRecord validator = itr.next();
            if(validator.getBalance() < Constants.EJECTION_BALANCE)
                state.update_validator_status(state, state.getValidator_registry().indexOf(validator), Constants.EXITED_WITHOUT_PENALTY);
        }
    }

    private static ArrayList<ValidatorRecord> to_penalize(ArrayList<ValidatorRecord> validator_registry){
        ArrayList<ValidatorRecord> to_penalize = new ArrayList<ValidatorRecord>();
        if(validator_registry != null) {
            Iterator<ValidatorRecord> itr = validator_registry.iterator();
            while (itr.hasNext()) {
                ValidatorRecord validator = itr.next();
                if (validator.getStatus().getValue() == Constants.EXITED_WITH_PENALTY) to_penalize.add(validator);
            }
        }
        return to_penalize;
    }

}
