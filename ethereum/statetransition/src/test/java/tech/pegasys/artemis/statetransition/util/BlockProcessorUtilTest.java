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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.newDeposits;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomValidator;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.ValidatorImpl;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BlockProcessorUtilTest {
  @Test
  @Disabled
  void processDepositAddsNewValidatorWhenPubkeyIsNotFoundInRegistry()
      throws BlockProcessingException {
    // Data Setup
    SSZList<DepositWithIndex> deposits = newDeposits(1);
    Deposit deposit = deposits.get(0);
    DepositData depositInput = deposit.getData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = deposit.getData().getAmount();

    MutableBeaconState beaconState = createBeaconState().createWritableCopy();

    int originalValidatorRegistrySize = beaconState.getValidators().size();
    int originalValidatorBalancesSize = beaconState.getBalances().size();

    // Attempt to process deposit with above data.
    BlockProcessorUtil.process_deposits(beaconState, deposits);

    assertTrue(
        beaconState.getValidators().size() == (originalValidatorRegistrySize + 1),
        "No validator was added to the validator registry.");
    assertTrue(
        beaconState.getBalances().size() == (originalValidatorBalancesSize + 1),
        "No balance was added to the validator balances.");
    assertEquals(
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            false,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH),
        beaconState.getValidators().get(originalValidatorRegistrySize));
    assertEquals(amount, beaconState.getBalances().get(originalValidatorBalancesSize));
  }

  @Test
  @Disabled
  void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry()
      throws BlockProcessingException {
    // Data Setup
    SSZList<DepositWithIndex> deposits = newDeposits(1);
    Deposit deposit = deposits.get(0);
    DepositData depositInput = deposit.getData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = deposit.getData().getAmount();

    ValidatorImpl knownValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            false,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH);

    MutableBeaconState beaconState = createBeaconState(amount, knownValidator).createWritableCopy();

    int originalValidatorRegistrySize = beaconState.getValidators().size();
    int originalValidatorBalancesSize = beaconState.getBalances().size();

    // Attempt to process deposit with above data.
    BlockProcessorUtil.process_deposits(beaconState, deposits);

    assertTrue(
        beaconState.getValidators().size() == originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertTrue(
        beaconState.getBalances().size() == originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(
        knownValidator, beaconState.getValidators().get(originalValidatorRegistrySize - 1));
    assertEquals(
        amount.times(UnsignedLong.valueOf(2L)),
        beaconState.getBalances().get(originalValidatorBalancesSize - 1));
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(UnsignedLong amount, ValidatorImpl knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UnsignedLong amount, ValidatorImpl knownValidator) {
    MutableBeaconState beaconState = BeaconState.createEmpty().createWritableCopy();
    beaconState.setSlot(randomUnsignedLong(100));
    beaconState.setFork(
        new Fork(
            Constants.GENESIS_FORK_VERSION,
            Constants.GENESIS_FORK_VERSION,
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));

    SSZMutableList<ValidatorImpl> validatorList =
        SSZList.create(
            Arrays.asList(randomValidator(101), randomValidator(102), randomValidator(103)),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            ValidatorImpl.class);
    SSZMutableList<UnsignedLong> balanceList =
        SSZList.create(
            Arrays.asList(
                randomUnsignedLong(104), randomUnsignedLong(105), randomUnsignedLong(106)),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            UnsignedLong.class);

    if (addToList) {
      validatorList.add(knownValidator);
      balanceList.add(amount);
    }

    beaconState.getValidators().addAll(validatorList);
    beaconState.getBalances().addAll(balanceList);
    return beaconState.commitChanges();
  }
}
