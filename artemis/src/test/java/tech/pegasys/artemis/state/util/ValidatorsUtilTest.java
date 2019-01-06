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

import static org.junit.Assert.*;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;

import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;

import org.junit.Test;

public class ValidatorsUtilTest {

    public static double DOUBLE_ASSERTION_DELTA = 0.0d;

    @Test
    public void assert_Get_Zero_As_Effective_Balance_For_NullValidators(){

        //given
        double effective_Balance_Expected = 0.0d;

        //when
        double effective_Balance_Actual = ValidatorsUtil.get_effective_balance(null);

        //then
        assertEquals(effective_Balance_Expected,effective_Balance_Actual,DOUBLE_ASSERTION_DELTA);
    }

    @Test
    public void assert_Get_Zero_As_Effective_Balance_For_Validators_With_SingleValidatorRecord_And_Zero_Balance(){

        //given
        int pubkey = 200;
        Hash withdrawal_credentials = Hash.ZERO;
        Hash randao_commitment = Hash.ZERO;
        UInt64 randao_layers = UInt64.MIN_VALUE;
        UInt64 status = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        UInt64 slot =  UInt64.valueOf(0);
        UInt64 exit_count = UInt64.MIN_VALUE;
        UInt64 last_poc_change_slot = UInt64.MIN_VALUE;
        UInt64 second_last_poc_change_slot = UInt64.MIN_VALUE;
        double balance = 0.0d;

        ValidatorRecord validatorRecord_TestInput =
                getAValidatorRecordTestDataFromParameters(pubkey,withdrawal_credentials,randao_commitment,randao_layers,
                        status,slot,exit_count,last_poc_change_slot,second_last_poc_change_slot,balance);


        // Add validator records
        ArrayList<ValidatorRecord> validatorRecords = new ArrayList<ValidatorRecord>();

        validatorRecords.add(validatorRecord_TestInput);

        Validators validators = new Validators(validatorRecords);

        //when
        double effective_Balance_Actual = ValidatorsUtil.get_effective_balance(validators);

        //then
        assertEquals(balance,effective_Balance_Actual,DOUBLE_ASSERTION_DELTA);
    }

    @Test
    public void assert_Get_NonZero_As_Effective_Balance_For_Validators_With_SingleValidatorRecord_And_NonZero_Balance(){

        //given
        int pubkey = 200;
        Hash withdrawal_credentials = Hash.ZERO;
        Hash randao_commitment = Hash.ZERO;
        UInt64 randao_layers = UInt64.MIN_VALUE;
        UInt64 status = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        UInt64 slot =  UInt64.valueOf(0);
        UInt64 exit_count = UInt64.MIN_VALUE;
        UInt64 last_poc_change_slot = UInt64.MIN_VALUE;
        UInt64 second_last_poc_change_slot = UInt64.MIN_VALUE;
        double balance = 112.32d;

        ValidatorRecord validatorRecord_TestInput =
                getAValidatorRecordTestDataFromParameters(pubkey,withdrawal_credentials,randao_commitment,randao_layers,
                        status,slot,exit_count,last_poc_change_slot,second_last_poc_change_slot,balance);


        // Add validator records
        ArrayList<ValidatorRecord> validatorRecords = new ArrayList<ValidatorRecord>();

        validatorRecords.add(validatorRecord_TestInput);

        Validators validators = new Validators(validatorRecords);

        //when
        double effective_Balance_Actual = ValidatorsUtil.get_effective_balance(validators);

        //then
        assertEquals(balance,effective_Balance_Actual,DOUBLE_ASSERTION_DELTA);
    }

    @Test
    public void assert_Get_NonZero_Value_As_Effective_Balance_For_Validators_With_MultipleValidatorRecords(){

        //given

        ArrayList<ValidatorRecord> validatorRecords = new ArrayList<ValidatorRecord>();

        int pubkey_ValidatorRecord1 = 200;
        Hash withdrawal_credentials_ValidatorRecord1 = Hash.ZERO;
        Hash randao_commitment_ValidatorRecord1 = Hash.ZERO;
        UInt64 randao_layers_ValidatorRecord1 = UInt64.MIN_VALUE;
        UInt64 status_ValidatorRecord1 = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        UInt64 slot_ValidatorRecord1 =  UInt64.valueOf(0);
        UInt64 exit_count_ValidatorRecord1 = UInt64.MIN_VALUE;
        UInt64 last_poc_change_slot_ValidatorRecord1 = UInt64.MIN_VALUE;
        UInt64 second_last_poc_change_slot_ValidatorRecord1 = UInt64.MIN_VALUE;
        double balance_ValidatorRecord1 = 112.32d;

        ValidatorRecord validatorRecord_TestInput_1 =
                getAValidatorRecordTestDataFromParameters(pubkey_ValidatorRecord1,withdrawal_credentials_ValidatorRecord1,
                        randao_commitment_ValidatorRecord1,randao_layers_ValidatorRecord1,
                        status_ValidatorRecord1,slot_ValidatorRecord1,exit_count_ValidatorRecord1,
                        last_poc_change_slot_ValidatorRecord1,
                        second_last_poc_change_slot_ValidatorRecord1,balance_ValidatorRecord1);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_1);

        int pubkey_ValidatorRecord2 = 200;
        Hash withdrawal_credentials_ValidatorRecord2 = Hash.ZERO;
        Hash randao_commitment_ValidatorRecord2 = Hash.ZERO;
        UInt64 randao_layers_ValidatorRecord2 = UInt64.MIN_VALUE;
        UInt64 status_ValidatorRecord2 = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        UInt64 slot_ValidatorRecord2 =  UInt64.valueOf(0);
        UInt64 exit_count_ValidatorRecord2 = UInt64.MIN_VALUE;
        UInt64 last_poc_change_slot_ValidatorRecord2 = UInt64.MIN_VALUE;
        UInt64 second_last_poc_change_slot_ValidatorRecord2 = UInt64.MIN_VALUE;
        double balance_ValidatorRecord2 = 100.445311d;

        ValidatorRecord validatorRecord_TestInput_2 =
                getAValidatorRecordTestDataFromParameters(pubkey_ValidatorRecord2,withdrawal_credentials_ValidatorRecord2,
                        randao_commitment_ValidatorRecord2,randao_layers_ValidatorRecord2,
                        status_ValidatorRecord2,slot_ValidatorRecord2,exit_count_ValidatorRecord2,
                        last_poc_change_slot_ValidatorRecord2,
                        second_last_poc_change_slot_ValidatorRecord2,balance_ValidatorRecord2);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_2);

        Validators validators = new Validators(validatorRecords);

        double effective_balance_Expected = balance_ValidatorRecord1+balance_ValidatorRecord2;


        //when
        double effective_Balance_Actual = ValidatorsUtil.get_effective_balance(validators);

        //then
        assertEquals(effective_balance_Expected,effective_Balance_Actual,DOUBLE_ASSERTION_DELTA);
    }


    @Test
    public void assert_Get_Active_Validator_Indices(){

        //given
        ArrayList<ValidatorRecord> validatorRecords = new ArrayList<ValidatorRecord>();

        ValidatorRecord validatorRecord_TestInput_1 = getDefaultValidatorRecord();
        UInt64 status_ValidatorRecord1 = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        validatorRecord_TestInput_1.setStatus(status_ValidatorRecord1);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_1);


        ValidatorRecord validatorRecord_TestInput_2 = getDefaultValidatorRecord();
        UInt64 status_ValidatorRecord2 = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        validatorRecord_TestInput_2.setStatus(status_ValidatorRecord2);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_2);

        Validators validators = new Validators(validatorRecords);

        long activeValidatorSize_Expected = 2;

        //when
        Validators activeValidators_Actual = ValidatorsUtil.get_active_validator_indices(validators);

        //then
        assertNotNull(activeValidators_Actual);
        assertEquals(activeValidatorSize_Expected,activeValidators_Actual.size());
        assertEquals(validatorRecord_TestInput_1,activeValidators_Actual.get(0));
        assertEquals(validatorRecord_TestInput_2,activeValidators_Actual.get(1));
    }


    @Test
    public void assert_That_Inactive_Validators_Are_excluded_For_Get_active_validator_indices(){

        //given

        ArrayList<ValidatorRecord> validatorRecords = new ArrayList<ValidatorRecord>();

        ValidatorRecord validatorRecord_TestInput_1 = getDefaultValidatorRecord();
        UInt64 status_ValidatorRecord1 = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        validatorRecord_TestInput_1.setStatus(status_ValidatorRecord1);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_1);


        ValidatorRecord validatorRecord_TestInput_2 = getDefaultValidatorRecord();
        UInt64 status_ValidatorRecord2 = UInt64.valueOf(EXITED_WITHOUT_PENALTY) ;
        validatorRecord_TestInput_2.setStatus(status_ValidatorRecord2);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_2);

        Validators validators = new Validators(validatorRecords);

        long activeValidatorSize_Expected = 1;

        //when
        Validators activeValidators_Actual = ValidatorsUtil.get_active_validator_indices(validators);

        //then
        assertNotNull(activeValidators_Actual);
        assertEquals(activeValidatorSize_Expected,activeValidators_Actual.size());
        assertTrue(activeValidators_Actual.contains(validatorRecord_TestInput_1));
        assertFalse(activeValidators_Actual.contains(validatorRecord_TestInput_2));
    }

    @Test
    public void assert_Get_active_validator_indices_For_All_Inactive_Validators_Scenario(){

        //given

        ArrayList<ValidatorRecord> validatorRecords = new ArrayList<ValidatorRecord>();

        ValidatorRecord validatorRecord_TestInput_1 = getDefaultValidatorRecord();
        UInt64 status_ValidatorRecord1 = UInt64.valueOf(EXITED_WITHOUT_PENALTY) ;
        validatorRecord_TestInput_1.setStatus(status_ValidatorRecord1);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_1);


        ValidatorRecord validatorRecord_TestInput_2 = getDefaultValidatorRecord();
        UInt64 status_ValidatorRecord2 = UInt64.valueOf(EXITED_WITHOUT_PENALTY) ;
        validatorRecord_TestInput_2.setStatus(status_ValidatorRecord2);

        // Add validator records
        validatorRecords.add(validatorRecord_TestInput_2);

        Validators validators = new Validators(validatorRecords);

        long activeValidatorSize_Expected = 0;

        //when
        Validators activeValidators_Actual = ValidatorsUtil.get_active_validator_indices(validators);

        //then
        assertNotNull(activeValidators_Actual);
        assertEquals(activeValidatorSize_Expected,activeValidators_Actual.size());
        assertFalse(activeValidators_Actual.contains(validatorRecord_TestInput_1));
        assertFalse(activeValidators_Actual.contains(validatorRecord_TestInput_2));
    }


    @Test
    public void assert_Get_Active_Validator_Indices_As_EmptyList_For_NullInput(){

        //given
        Validators validators_Input = null;
        long activeValidatorSize_Expected = 0;

        //when
        Validators activeValidators_Actual = ValidatorsUtil.get_active_validator_indices(validators_Input);

        //then
        assertNotNull(activeValidators_Actual);
        assertEquals(activeValidatorSize_Expected,activeValidators_Actual.size());
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

    public ValidatorRecord getDefaultValidatorRecord(){

        int pubkey = 0;
        Hash withdrawal_credentials = Hash.ZERO;
        Hash randao_commitment = Hash.ZERO;
        UInt64 randao_layers = UInt64.MIN_VALUE;
        UInt64 status = UInt64.valueOf(ACTIVE_PENDING_EXIT) ;
        UInt64 slot =  UInt64.valueOf(0);
        UInt64 exit_count = UInt64.MIN_VALUE;
        UInt64 last_poc_change_slot = UInt64.MIN_VALUE;
        UInt64 second_last_poc_change_slot = UInt64.MIN_VALUE;
        double balance = 0.0d;

        return  getAValidatorRecordTestDataFromParameters(pubkey,withdrawal_credentials,
                randao_commitment,randao_layers,
                status,slot,exit_count,
                last_poc_change_slot,
                second_last_poc_change_slot,balance);
    }


}
