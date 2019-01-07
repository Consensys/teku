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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt64;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;

public class ValidatorsUtilTest {

    public static final double DOUBLE_ASSERTION_DELTA = 0.0d;

    public static final double DEFAULT_BALANCE = 0.0d;

    private int validator_size_expected = 0;

    private double effective_balance_expected = 0.0d;

    private double balance;

    private Validators validatorRecord_testInput;

    @Before
    public void init_test(){
        validator_size_expected = 0;
        effective_balance_expected = 0.0d;
        balance = 0.0d;
        validatorRecord_testInput = null;
    }

    @After
    public void cleanup_test(){
        validator_size_expected = 0;
        effective_balance_expected = 0.0d;
        validatorRecord_testInput = null;
    }

    @Test
    public void assert_get_zero_as_effective_balance_for_nullValidators(){

        //when
        double effective_balance_actual = ValidatorsUtil.get_effective_balance(null);

        //then
        assertEquals(effective_balance_expected,effective_balance_actual,DOUBLE_ASSERTION_DELTA);
    }

    @Test
    public void assert_get_zero_as_effective_balance_for_validators_with_singleValidatorRecord_and_zero_balance(){

        //given
        validatorRecord_testInput =  getValidatorsList(getDefaultValidatorRecordWithStatus(200,ACTIVE_PENDING_EXIT,DEFAULT_BALANCE));

        //when
        double effective_balance_actual = ValidatorsUtil.get_effective_balance(validatorRecord_testInput);

        //then
        assertEquals(effective_balance_expected,effective_balance_actual,DOUBLE_ASSERTION_DELTA);
    }

    @Test
    public void assert_get_nonZero_as_effective_balance_for_validators_with_singleValidatorRecord_and_nonZero_balance(){

        //given
        balance = 112.32d;

        effective_balance_expected = balance;

        validatorRecord_testInput =  getValidatorsList(getDefaultValidatorRecordWithStatus(200,ACTIVE_PENDING_EXIT,balance));

        //when
        double effective_balance_actual = ValidatorsUtil.get_effective_balance(validatorRecord_testInput);

        //then
        assertEquals(effective_balance_expected,effective_balance_actual,DOUBLE_ASSERTION_DELTA);
    }

    @Test
    public void assert_get_nonZero_value_as_effective_balance_for_validators_with_multipleValidatorRecords(){

        //given
        double balance_validatorRecord1 = 112.32d;

        double balance_validatorRecord2 = 100.445311d;

        validatorRecord_testInput = getValidatorsList(
                getDefaultValidatorRecordWithStatus(100,ACTIVE_PENDING_EXIT,balance_validatorRecord1),
                getDefaultValidatorRecordWithStatus(200,ACTIVE_PENDING_EXIT,balance_validatorRecord2));

        effective_balance_expected = balance_validatorRecord1+balance_validatorRecord2;

        //when
        double effective_Balance_Actual = ValidatorsUtil.get_effective_balance(validatorRecord_testInput);

        //then
        assertEquals(effective_balance_expected,effective_Balance_Actual,DOUBLE_ASSERTION_DELTA);
    }


    @Test
    public void assert_get_active_validator_indices(){

        //given
        validatorRecord_testInput = getValidatorsList(
        getDefaultValidatorRecordWithStatus(100,ACTIVE_PENDING_EXIT,DEFAULT_BALANCE),
        getDefaultValidatorRecordWithStatus(200,ACTIVE_PENDING_EXIT,DEFAULT_BALANCE));

        validator_size_expected = 2;

        //when
        Validators activeValidators_Actual = ValidatorsUtil.get_active_validator_indices(validatorRecord_testInput);

        //then
        assertNotNull(activeValidators_Actual);
        assertEquals(validator_size_expected,activeValidators_Actual.size());
        assertEquals(validatorRecord_testInput.get(0),activeValidators_Actual.get(0));
        assertEquals(validatorRecord_testInput.get(1),activeValidators_Actual.get(1));
    }


    @Test
    public void assert_that_inactive_validators_are_excluded_for_get_active_validator_indices(){

        //given
        validatorRecord_testInput = getValidatorsList(
                getDefaultValidatorRecordWithStatus(100,ACTIVE_PENDING_EXIT,DEFAULT_BALANCE),
                getDefaultValidatorRecordWithStatus(200,EXITED_WITHOUT_PENALTY,DEFAULT_BALANCE));

        validator_size_expected = 1;

        //when
        Validators active_validators_actual = ValidatorsUtil.get_active_validator_indices(validatorRecord_testInput);

        //then
        assertNotNull(active_validators_actual);
        assertEquals(validator_size_expected,active_validators_actual.size());
        assertTrue(active_validators_actual.contains(validatorRecord_testInput.get(0)));
        assertFalse(active_validators_actual.contains(validatorRecord_testInput.get(1)));
    }

    @Test
    public void assert_get_active_validator_indices_for_all_inactive_validators_scenario(){

        //given
        validatorRecord_testInput = getValidatorsList(
                getDefaultValidatorRecordWithStatus(100,EXITED_WITHOUT_PENALTY,DEFAULT_BALANCE),
                getDefaultValidatorRecordWithStatus(200,EXITED_WITHOUT_PENALTY,DEFAULT_BALANCE));

        validator_size_expected = 0;

        //when
        Validators active_validators_actual = ValidatorsUtil.get_active_validator_indices(validatorRecord_testInput);

        //then
        assertNotNull(active_validators_actual);
        assertEquals(validator_size_expected,active_validators_actual.size());
        assertFalse(active_validators_actual.contains(validatorRecord_testInput.get(0)));
        assertFalse(active_validators_actual.contains(validatorRecord_testInput.get(1)));
    }


    @Test
    public void assert_get_active_validator_indices_as_emptyList_for_nullInput(){

        //given
        validator_size_expected = 0;

        //when
        Validators active_validators_actual = ValidatorsUtil.get_active_validator_indices(null);

        //then
        assertNotNull(active_validators_actual);
        assertEquals(validator_size_expected,active_validators_actual.size());
    }


    public ValidatorRecord getAValidatorRecordTestDataFromParameters(int pubkey, Hash withdrawal_credentials, Hash randao_commitment, UInt64 randao_layers,
                                                                     UInt64 status, UInt64 slot, UInt64 exit_count, UInt64 last_poc_change_slot,
                                                                     UInt64 second_last_poc_change_slot,double balance){

        ValidatorRecord validatorRecord =
                new ValidatorRecord( pubkey,  withdrawal_credentials,  randao_commitment,  randao_layers,
                status,  slot,  exit_count,  last_poc_change_slot, second_last_poc_change_slot);

        validatorRecord.setBalance(balance);

        return validatorRecord;
    }


    public ValidatorRecord getDefaultValidatorRecordWithStatus(int pubKey,int statusAsInt,double balance){

        Hash withdrawal_credentials = Hash.ZERO;
        Hash randao_commitment = Hash.ZERO;
        UInt64 randao_layers = UInt64.MIN_VALUE;
        UInt64 status = UInt64.valueOf(statusAsInt) ;
        UInt64 slot =  UInt64.valueOf(0);
        UInt64 exit_count = UInt64.MIN_VALUE;
        UInt64 last_poc_change_slot = UInt64.MIN_VALUE;
        UInt64 second_last_poc_change_slot = UInt64.MIN_VALUE;


        return  getAValidatorRecordTestDataFromParameters(pubKey,withdrawal_credentials,
                randao_commitment,randao_layers,
                status,slot,exit_count,
                last_poc_change_slot,
                second_last_poc_change_slot,balance);
    }

    public Validators getValidatorsList(ValidatorRecord... validatorRecords){
        return new Validators(Arrays.asList(validatorRecords));
    }
}