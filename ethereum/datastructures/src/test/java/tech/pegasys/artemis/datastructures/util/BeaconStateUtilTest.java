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

import static java.lang.Math.toIntExact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_power_of_two;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.newDeposits;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.bls.BLSVerify;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateUtilTest {
  @Test
  void sqrtOfSquareNumber() {
    long actual = BeaconStateUtil.integer_squareroot(3481L);
    long expected = 59L;
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANonSquareNumber() {
    long actual = BeaconStateUtil.integer_squareroot(27L);
    long expected = 5L;
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANegativeNumber() {
    assertThrows(IllegalArgumentException.class, () -> BeaconStateUtil.integer_squareroot(-1L));
  }

  // TODO It may make sense to move these tests to a Fork specific test class in the future.
  // *************** START Fork Tests ***************
  @Test
  void getForkVersionReturnsPreviousVersionWhenGivenEpochIsLessThanForkEpoch() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    long previousVersion = 0;
    long currentVersion = previousVersion + 1L;

    // Setup Epochs
    // It is necessary for this test that givenEpoch is less than forkEpoch.
    long givenEpoch = 100L;
    long forkEpoch = givenEpoch + 1L;

    // Setup Fork
    Fork fork = new Fork(previousVersion, currentVersion, forkEpoch);

    assertEquals(BeaconStateUtil.get_fork_version(fork, givenEpoch), previousVersion);
  }

  @Test
  void getForkVersionReturnsCurrentVersionWhenGivenEpochIsGreaterThanForkEpoch() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    long previousVersion = 0;
    long currentVersion = previousVersion + 1L;

    // Setup Epochs
    // It is necessary for this test that givenEpoch is greater than forkEpoch.
    long forkEpoch = 100L;
    long givenEpoch = forkEpoch + 1L;

    // Setup Fork
    Fork fork = new Fork(previousVersion, currentVersion, forkEpoch);

    assertEquals(BeaconStateUtil.get_fork_version(fork, givenEpoch), currentVersion);
  }

  @Test
  void getDomainReturnsAsExpectedForAllSignatureDomainTypesWithPreviousVersionFork() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    long previousVersion = 0;
    long currentVersion = previousVersion + 1L;

    // Setup Epochs
    long givenEpoch = 100L;
    long forkEpoch = givenEpoch + 1L;

    // Setup Fork
    Fork fork = new Fork(previousVersion, currentVersion, forkEpoch);

    // Iterate Over the Possible Signature Domains
    // 0 - DOMAIN_DEPOSIT
    // 1 - DOMAIN_ATTESTATION
    // 2 - DOMAIN_PROPOSAL
    // 3 - DOMAIN_EXIT
    // 4 - DOMAIN_RANDAO
    for (int domain = 0; domain <= 4; ++domain) {
      assertEquals(
          BeaconStateUtil.get_domain(fork, givenEpoch, domain),
          (BeaconStateUtil.get_fork_version(fork, givenEpoch) << 32) + domain);
    }
  }

  @Test
  void getDomainReturnsAsExpectedForAllSignatureDomainTypesWithCurrentVersionFork() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    long previousVersion = 0;
    long currentVersion = previousVersion + 1L;

    // Setup Epochs
    long forkEpoch = 100L;
    long givenEpoch = forkEpoch + 1L;

    // Setup Fork
    Fork fork = new Fork(previousVersion, currentVersion, forkEpoch);

    // Iterate Over the Possible Signature Domains
    // 0 - DOMAIN_DEPOSIT
    // 1 - DOMAIN_ATTESTATION
    // 2 - DOMAIN_PROPOSAL
    // 3 - DOMAIN_EXIT
    // 4 - DOMAIN_RANDAO
    for (int domain = 0; domain <= 4; ++domain) {
      assertEquals(
          BeaconStateUtil.get_domain(fork, givenEpoch, domain),
          (BeaconStateUtil.get_fork_version(fork, givenEpoch) << 32) + domain);
    }
  }
  // *************** END Fork Tests ***************

  @Test
  void validateProofOfPosessionReturnsTrueIfTheBLSSignatureIsValidForGivenDepositInputData() {
    Deposit deposit = newDeposits(1).get(0);
    BLSPublicKey pubkey = deposit.getDeposit_data().getDeposit_input().getPubkey();
    BLSSignature proofOfPossession =
        deposit.getDeposit_data().getDeposit_input().getProof_of_possession();
    long domain =
        BeaconStateUtil.get_domain(
            new Fork(
                Constants.GENESIS_FORK_VERSION,
                Constants.GENESIS_FORK_VERSION,
                Constants.GENESIS_EPOCH),
            Constants.GENESIS_EPOCH,
            Constants.DOMAIN_DEPOSIT);

    assertTrue(
        BLSVerify.bls_verify(
            pubkey,
            deposit.getDeposit_data().getDeposit_input().signedRoot("proof_of_possession"),
            proofOfPossession,
            domain));
  }

  @Test
  void validateProofOfPosessionReturnsFalseIfTheBLSSignatureIsNotValidForGivenDepositInputData() {
    Deposit deposit = newDeposits(1).get(0);
    BLSPublicKey pubkey = BLSPublicKey.random();
    BLSSignature proofOfPossession =
        deposit.getDeposit_data().getDeposit_input().getProof_of_possession();
    long domain =
        BeaconStateUtil.get_domain(
            new Fork(
                Constants.GENESIS_FORK_VERSION,
                Constants.GENESIS_FORK_VERSION,
                Constants.GENESIS_EPOCH),
            Constants.GENESIS_EPOCH,
            Constants.DOMAIN_DEPOSIT);

    assertFalse(
        BLSVerify.bls_verify(
            pubkey,
            deposit.getDeposit_data().getDeposit_input().signedRoot("proof_of_possession"),
            proofOfPossession,
            domain));
  }

  @Test
  void processDepositAddsNewValidatorWhenPubkeyIsNotFoundInRegistry() {
    // Data Setup
    Deposit deposit = newDeposits(1).get(0);
    DepositInput depositInput = deposit.getDeposit_data().getDeposit_input();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    long amount = deposit.getDeposit_data().getAmount();

    BeaconState beaconState = createBeaconState();

    int originalValidatorRegistrySize = beaconState.getValidator_registry().size();
    int originalValidatorBalancesSize = beaconState.getValidator_balances().size();

    // Attempt to process deposit with above data.
    BeaconStateUtil.process_deposit(beaconState, deposit);

    assertEquals(
        beaconState.getValidator_registry().size(),
        (originalValidatorRegistrySize + 1),
        "No validator was added to the validator registry.");
    assertEquals(
        beaconState.getValidator_balances().size(),
        (originalValidatorBalancesSize + 1),
        "No balance was added to the validator balances.");
    assertEquals(
        new Validator(
            pubkey,
            withdrawalCredentials,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            false,
            false),
        beaconState.getValidator_registry().get(originalValidatorRegistrySize));
    assertEquals(
        amount, (long) beaconState.getValidator_balances().get(originalValidatorBalancesSize));
  }

  @Test
  void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry() {
    // Data Setup
    Deposit deposit = newDeposits(1).get(0);
    DepositInput depositInput = deposit.getDeposit_data().getDeposit_input();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    long amount = deposit.getDeposit_data().getAmount();

    Validator knownValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            false,
            false);

    BeaconState beaconState = createBeaconState(amount, knownValidator);

    int originalValidatorRegistrySize = beaconState.getValidator_registry().size();
    int originalValidatorBalancesSize = beaconState.getValidator_balances().size();

    // Attempt to process deposit with above data.
    BeaconStateUtil.process_deposit(beaconState, deposit);

    assertEquals(
        beaconState.getValidator_registry().size(),
        originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertEquals(
        beaconState.getValidator_balances().size(),
        originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(
        knownValidator, beaconState.getValidator_registry().get(originalValidatorRegistrySize - 1));
    assertEquals(
        amount * 2L,
        (long) beaconState.getValidator_balances().get(originalValidatorBalancesSize - 1));
  }

  @Test
  void getTotalBalanceAddsAndReturnsEffectiveTotalBalancesCorrectly() {
    // Data Setup
    BeaconState state = createBeaconState();
    CrosslinkCommittee crosslinkCommittee =
        new CrosslinkCommittee(
            1, Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));

    // Calculate Expected Results
    long expectedBalance = 0;
    for (long balance : state.getValidator_balances()) {
      if (balance < Constants.MAX_DEPOSIT_AMOUNT) {
        expectedBalance = expectedBalance + balance;
      } else {
        expectedBalance = expectedBalance + Constants.MAX_DEPOSIT_AMOUNT;
      }
    }

    assertEquals(expectedBalance, BeaconStateUtil.get_total_balance(state, crosslinkCommittee));
  }

  @Test
  void penalizeValidatorDecrementsBadActorAndIncrementsWhistleblower() {
    // Actual Data Setup
    BeaconState beaconState = createBeaconState();
    int validatorIndex = 1;

    beaconState.setCurrent_shuffling_epoch(Constants.FAR_FUTURE_EPOCH);
    beaconState.setPrevious_shuffling_epoch(Constants.FAR_FUTURE_EPOCH);
    List<Long> latestPenalizedBalances = Collections.nCopies(64, randomLong());
    latestPenalizedBalances.addAll(
        Collections.nCopies(Constants.LATEST_SLASHED_EXIT_LENGTH - 64, 0L));
    beaconState.setLatest_slashed_balances(latestPenalizedBalances);

    // Expected Data Setup
    int whistleblowerIndex =
        BeaconStateUtil.get_beacon_proposer_index(beaconState, beaconState.getSlot());
    long whistleblowerReward =
        BeaconStateUtil.get_effective_balance(beaconState, validatorIndex)
            / Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
    long whistleblowerBalance = beaconState.getValidator_balances().get(whistleblowerIndex);

    long validatorBalance = beaconState.getValidator_balances().get(validatorIndex);

    long expectedWhistleblowerBalance = whistleblowerBalance + whistleblowerReward;
    long expectedBadActorBalance = validatorBalance - whistleblowerReward;

    // Penalize validator in above beacon state at validatorIndex.
    BeaconStateUtil.penalize_validator(beaconState, validatorIndex);

    assertEquals(
        expectedBadActorBalance,
        beaconState.getValidator_balances().get(validatorIndex).longValue());
    assertEquals(
        expectedWhistleblowerBalance,
        beaconState.getValidator_balances().get(whistleblowerIndex).longValue());
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(Constants.GENESIS_SLOT);
    assertEquals(Constants.GENESIS_EPOCH, BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(Constants.GENESIS_SLOT + Constants.SLOTS_PER_EPOCH);
    assertEquals(Constants.GENESIS_EPOCH, BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(Constants.GENESIS_SLOT + 2 * Constants.SLOTS_PER_EPOCH);
    assertEquals(Constants.GENESIS_EPOCH + 1, BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetNextEpochReturnsTheEpochPlusOne() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(Constants.GENESIS_SLOT);
    assertEquals(Constants.GENESIS_EPOCH + 1, BeaconStateUtil.get_next_epoch(beaconState));
  }

  @Test
  void intToBytes() {
    long value = 0x0123456789abcdefL;
    assertEquals(Bytes.EMPTY, BeaconStateUtil.int_to_bytes(value, 0));
    assertEquals(Bytes.fromHexString("0xef"), BeaconStateUtil.int_to_bytes(value, 1));
    assertEquals(Bytes.fromHexString("0xefcd"), BeaconStateUtil.int_to_bytes(value, 2));
    assertEquals(Bytes.fromHexString("0xefcdab89"), BeaconStateUtil.int_to_bytes(value, 4));
    assertEquals(Bytes.fromHexString("0xefcdab8967452301"), BeaconStateUtil.int_to_bytes(value, 8));
    assertEquals(
        Bytes.fromHexString("0xefcdab89674523010000000000000000"),
        BeaconStateUtil.int_to_bytes(value, 16));
    assertEquals(
        Bytes.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes(value, 32));
  }

  @Test
  void intToBytes32Long() {
    assertEquals(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(0L));
    assertEquals(
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(1L));
    assertEquals(
        Bytes32.fromHexString("0xffffffffffffffff000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(-1L));
    assertEquals(
        Bytes32.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(0x0123456789abcdefL));
  }

  @Test
  void bytesToInt() {
    assertEquals(0L, BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0x00")));
    assertEquals(1L, BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0x01")));
    assertEquals(1L, BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0x0100000000000000")));
    assertEquals(
        0x123456789abcdef0L,
        BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0xf0debc9a78563412")));
  }

  void isPowerOfTwo() {
    // Not powers of two:
    assertThat(is_power_of_two(0)).isEqualTo(false);
    assertThat(is_power_of_two(42L)).isEqualTo(false);
    assertThat(is_power_of_two(-1L)).isEqualTo(false);
    // Powers of two:
    assertThat(is_power_of_two(1)).isEqualTo(true);
    assertThat(is_power_of_two(1 + 1)).isEqualTo(true);
    assertThat(is_power_of_two(0x040000L)).isEqualTo(true);
    assertThat(is_power_of_two(0x0100000000L)).isEqualTo(true);
    assertThat(is_power_of_two(0x8000000000000000L)).isEqualTo(true);
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, 0, null);
  }

  private BeaconState createBeaconState(long amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(boolean addToList, long amount, Validator knownValidator) {
    BeaconState beaconState = new BeaconStateWithCache();
    beaconState.setSlot(randomLong());
    beaconState.setFork(
        new Fork(
            Constants.GENESIS_FORK_VERSION,
            Constants.GENESIS_FORK_VERSION,
            Constants.GENESIS_EPOCH));

    List<Validator> validatorList = Collections.nCopies(64, randomValidator());;
    List<Long> balanceList = Collections.nCopies(64, randomLong());;
    if (addToList) {
      validatorList.add(knownValidator);
      balanceList.add(amount);
    }

    beaconState.setValidator_registry(validatorList);
    beaconState.setValidator_balances(balanceList);
    return beaconState;
  }

  // *************** START Shuffling Tests ***************

  // TODO: tests for get_shuffling() - the reference tests are out of date.

  // The following are just sanity checks. The real testing is against the official test vectors,
  // elsewhere.

  @Test
  void succeedsWhenGetPermutedIndexReturnsAPermutation() {
    Bytes32 seed = Bytes32.random();
    int listSize = 1000;
    boolean[] done = new boolean[listSize]; // Initialised to false
    for (int i = 0; i < listSize; i++) {
      int idx = BeaconStateUtil.get_permuted_index(i, listSize, seed);
      assertFalse(done[idx]);
      done[idx] = true;
    }
  }

  @Test
  void succeedsWhenGetPermutedIndexAndShuffleGiveTheSameResults() {
    Bytes32 seed = Bytes32.random();
    int listSize = 1 + toIntExact(randomLong()) % 1000;
    int[] shuffling = BeaconStateUtil.shuffle(listSize, seed);
    for (int i = 0; i < listSize; i++) {
      int idx = BeaconStateUtil.get_permuted_index(i, listSize, seed);
      assertEquals(shuffling[i], idx);
    }
  }

  // *************** END Shuffling Tests *****************
}
