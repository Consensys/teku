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
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

@ExtendWith(BouncyCastleExtension.class)
class BlockProcessorUtilTest {
  @Test
  @Disabled
  void processDepositAddsNewValidatorWhenPubkeyIsNotFoundInRegistry()
      throws BlockProcessingException {
    // Data Setup
    List<DepositWithIndex> deposits = newDeposits(1);
    Deposit deposit = deposits.get(0);
    DepositData depositInput = deposit.getData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = deposit.getData().getAmount();

    BeaconState beaconState = createBeaconState();

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
        new Validator(
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
    List<DepositWithIndex> deposits = newDeposits(1);
    Deposit deposit = deposits.get(0);
    DepositData depositInput = deposit.getData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = deposit.getData().getAmount();

    Validator knownValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            false,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH);

    BeaconState beaconState = createBeaconState(amount, knownValidator);

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

  private BeaconState createBeaconState(UnsignedLong amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UnsignedLong amount, Validator knownValidator) {
    BeaconState beaconState = new BeaconStateWithCache();
    beaconState.setSlot(randomUnsignedLong());
    beaconState.setFork(
        new Fork(
            new Bytes4(Bytes.ofUnsignedInt(0)),
            new Bytes4(Bytes.ofUnsignedInt(0)),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));

    SSZList<Validator> validatorList =
        new SSZList<>(
            Arrays.asList(randomValidator(), randomValidator(), randomValidator()),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            Validator.class);
    SSZList<UnsignedLong> balanceList =
        new SSZList<>(
            Arrays.asList(randomUnsignedLong(), randomUnsignedLong(), randomUnsignedLong()),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            UnsignedLong.class);

    if (addToList) {
      validatorList.add(knownValidator);
      balanceList.add(amount);
    }

    beaconState.setValidators(validatorList);
    beaconState.setBalances(balanceList);
    return beaconState;
  }
}
