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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDepositInput;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomValidator;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateUtilTest {
  @Test
  void minReturnsMin() {
    UnsignedLong actual = BeaconStateUtil.min(UnsignedLong.valueOf(13L), UnsignedLong.valueOf(12L));
    UnsignedLong expected = UnsignedLong.valueOf(12L);
    assertEquals(expected, actual);
  }

  @Test
  void minReturnsMinWhenEqual() {
    UnsignedLong actual = BeaconStateUtil.min(UnsignedLong.valueOf(12L), UnsignedLong.valueOf(12L));
    UnsignedLong expected = UnsignedLong.valueOf(12L);
    assertEquals(expected, actual);
  }

  @Test
  void maxReturnsMax() {
    UnsignedLong actual = BeaconStateUtil.max(UnsignedLong.valueOf(13L), UnsignedLong.valueOf(12L));
    UnsignedLong expected = UnsignedLong.valueOf(13L);
    assertEquals(expected, actual);
  }

  @Test
  void maxReturnsMaxWhenEqual() {
    UnsignedLong actual = BeaconStateUtil.max(UnsignedLong.valueOf(13L), UnsignedLong.valueOf(13L));
    UnsignedLong expected = UnsignedLong.valueOf(13L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfSquareNumber() {
    UnsignedLong actual = BeaconStateUtil.integer_squareroot(UnsignedLong.valueOf(3481L));
    UnsignedLong expected = UnsignedLong.valueOf(59L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANonSquareNumber() {
    UnsignedLong actual = BeaconStateUtil.integer_squareroot(UnsignedLong.valueOf(27L));
    UnsignedLong expected = UnsignedLong.valueOf(5L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANegativeNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          BeaconStateUtil.integer_squareroot(UnsignedLong.valueOf(-1L));
        });
  }

  // TODO It may make sense to move these tests to a Fork specific test class in the future.
  // *************** START Fork Tests ***************
  @Test
  void getForkVersionReturnsPreviousVersionWhenGivenEpochIsLessThanForkEpoch() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    UnsignedLong previousVersion = UnsignedLong.ZERO;
    UnsignedLong currentVersion = previousVersion.plus(UnsignedLong.valueOf(1L));

    // Setup Epochs
    // It is necessary for this test that givenEpoch is less than forkEpoch.
    UnsignedLong givenEpoch = UnsignedLong.valueOf(100L);
    UnsignedLong forkEpoch = givenEpoch.plus(UnsignedLong.valueOf(1L));

    // Setup Fork
    Fork fork = new Fork(previousVersion, currentVersion, forkEpoch);

    assertEquals(BeaconStateUtil.get_fork_version(fork, givenEpoch), previousVersion);
  }

  @Test
  void getForkVersionReturnsCurrentVersionWhenGivenEpochIsGreaterThanForkEpoch() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    UnsignedLong previousVersion = UnsignedLong.ZERO;
    UnsignedLong currentVersion = previousVersion.plus(UnsignedLong.valueOf(1L));

    // Setup Epochs
    // It is necessary for this test that givenEpoch is greater than forkEpoch.
    UnsignedLong forkEpoch = UnsignedLong.valueOf(100L);
    UnsignedLong givenEpoch = forkEpoch.plus(UnsignedLong.valueOf(1L));

    // Setup Fork
    Fork fork = new Fork(previousVersion, currentVersion, forkEpoch);

    assertEquals(BeaconStateUtil.get_fork_version(fork, givenEpoch), currentVersion);
  }

  @Test
  void getDomainReturnsAsExpectedForAllSignatureDomainTypesWithPreviousVersionFork() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    UnsignedLong previousVersion = UnsignedLong.ZERO;
    UnsignedLong currentVersion = previousVersion.plus(UnsignedLong.valueOf(1L));

    // Setup Epochs
    UnsignedLong givenEpoch = UnsignedLong.valueOf(100L);
    UnsignedLong forkEpoch = givenEpoch.plus(UnsignedLong.valueOf(1L));

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
          UnsignedLong.valueOf(
              (BeaconStateUtil.get_fork_version(fork, givenEpoch).longValue() << 32) + domain));
    }
  }

  @Test
  void getDomainReturnsAsExpectedForAllSignatureDomainTypesWithCurrentVersionFork() {
    // Setup Fork Versions
    // The values of these don't really matter, it just makes sense that
    // previous version is less than current version.
    UnsignedLong previousVersion = UnsignedLong.ZERO;
    UnsignedLong currentVersion = previousVersion.plus(UnsignedLong.valueOf(1L));

    // Setup Epochs
    UnsignedLong forkEpoch = UnsignedLong.valueOf(100L);
    UnsignedLong givenEpoch = forkEpoch.plus(UnsignedLong.valueOf(1L));

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
          UnsignedLong.valueOf(
              (BeaconStateUtil.get_fork_version(fork, givenEpoch).longValue() << 32) + domain));
    }
  }
  // *************** END Fork Tests ***************

  @Test
  @Disabled
  // TODO Fill out and enable this test case when bls_verify is complete.
  void validateProofOfPosessionReturnsTrueIfTheBLSSignatureIsValidForGivenDepositInputData() {
    BeaconState beaconState = null;
    BLSPublicKey pubkey = null;
    BLSSignature proofOfPossession = null;
    Bytes32 withdrawalCredentials = null;

    assertTrue(
        BeaconStateUtil.validate_proof_of_possession(
            beaconState, pubkey, proofOfPossession, withdrawalCredentials));
  }

  @Test
  @Disabled
  // TODO Fill out and enable this test case when bls_verify is complete.
  void validateProofOfPosessionReturnsFalseIfTheBLSSignatureIsNotValidForGivenDepositInputData() {
    BeaconState beaconState = null;
    BLSPublicKey pubkey = null;
    BLSSignature proofOfPossession = null;
    Bytes32 withdrawalCredentials = null;

    assertFalse(
        BeaconStateUtil.validate_proof_of_possession(
            beaconState, pubkey, proofOfPossession, withdrawalCredentials));
  }

  @Test
  void processDepositAddsNewValidatorWhenPubkeyIsNotFoundInRegistry() {
    // Data Setup
    DepositInput depositInput = randomDepositInput();
    BLSPublicKey pubkey = depositInput.getPubkey();
    BLSSignature proofOfPossession = depositInput.getProof_of_possession();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = UnsignedLong.valueOf(100L);

    BeaconState beaconState = createBeaconState();

    int originalValidatorRegistrySize = beaconState.getValidator_registry().size();
    int originalValidatorBalancesSize = beaconState.getValidator_balances().size();

    // Attempt to process deposit with above data.
    BeaconStateUtil.process_deposit(
        beaconState, pubkey, amount, proofOfPossession, withdrawalCredentials);

    assertTrue(
        beaconState.getValidator_registry().size() == (originalValidatorRegistrySize + 1),
        "No validator was added to the validator registry.");
    assertTrue(
        beaconState.getValidator_balances().size() == (originalValidatorBalancesSize + 1),
        "No balance was added to the validator balances.");
    assertEquals(
        new Validator(
            pubkey,
            withdrawalCredentials,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            UnsignedLong.ZERO),
        beaconState.getValidator_registry().get(originalValidatorRegistrySize));
    assertEquals(amount, beaconState.getValidator_balances().get(originalValidatorBalancesSize));
  }

  @Test
  void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry() {
    // Data Setup
    DepositInput depositInput = randomDepositInput();
    BLSPublicKey pubkey = depositInput.getPubkey();
    BLSSignature proofOfPossession = depositInput.getProof_of_possession();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = UnsignedLong.valueOf(100L);

    Validator knownValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            UnsignedLong.ZERO);

    BeaconState beaconState = createBeaconState(amount, knownValidator);

    int originalValidatorRegistrySize = beaconState.getValidator_registry().size();
    int originalValidatorBalancesSize = beaconState.getValidator_balances().size();

    // Attempt to process deposit with above data.
    BeaconStateUtil.process_deposit(
        beaconState, pubkey, amount, proofOfPossession, withdrawalCredentials);

    assertTrue(
        beaconState.getValidator_registry().size() == originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertTrue(
        beaconState.getValidator_balances().size() == originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(
        knownValidator, beaconState.getValidator_registry().get(originalValidatorRegistrySize - 1));
    assertEquals(
        amount.times(UnsignedLong.valueOf(2L)),
        beaconState.getValidator_balances().get(originalValidatorBalancesSize - 1));
  }

  @Test
  @Disabled // Pending resolution of Issue #347.
  void penalizeValidatorDecrementsBadActorAndIncrementsWhistleblower() {
    // Actual Data Setup
    BeaconState beaconState = createBeaconState();
    int validatorIndex = 1;

    beaconState.setCurrent_calculation_epoch(Constants.FAR_FUTURE_EPOCH);
    beaconState.setPrevious_calculation_epoch(Constants.FAR_FUTURE_EPOCH);
    List<UnsignedLong> latestPenalizedBalances =
        new ArrayList<>(
            Arrays.asList(randomUnsignedLong(), randomUnsignedLong(), randomUnsignedLong()));
    beaconState.setLatest_penalized_balances(latestPenalizedBalances);

    // Expected Data Setup
    int whistleblowerIndex =
        BeaconStateUtil.get_beacon_proposer_index(beaconState, beaconState.getSlot());
    UnsignedLong whistleblowerReward =
        BeaconStateUtil.get_effective_balance(beaconState, validatorIndex)
            .dividedBy(UnsignedLong.valueOf(Constants.WHISTLEBLOWER_REWARD_QUOTIENT));
    UnsignedLong whistleblowerBalance = beaconState.getValidator_balances().get(whistleblowerIndex);

    UnsignedLong validatorBalance = beaconState.getValidator_balances().get(validatorIndex);

    UnsignedLong expectedWhistleblowerBalance = whistleblowerBalance.plus(whistleblowerReward);
    UnsignedLong expectedBadActorBalance = validatorBalance.minus(whistleblowerReward);

    // Penalize validator in above beacon state at validatorIndex.
    BeaconStateUtil.penalize_validator(beaconState, validatorIndex);

    assertEquals(expectedBadActorBalance, beaconState.getValidator_balances().get(validatorIndex));
    assertEquals(
        expectedWhistleblowerBalance, beaconState.getValidator_balances().get(whistleblowerIndex));
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(UnsignedLong amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UnsignedLong amount, Validator knownValidator) {
    BeaconState beaconState = new BeaconState();
    beaconState.setSlot(randomUnsignedLong());
    beaconState.setFork(
        new Fork(
            UnsignedLong.valueOf(Constants.GENESIS_FORK_VERSION),
            UnsignedLong.valueOf(Constants.GENESIS_FORK_VERSION),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));

    List<Validator> validatorList =
        new ArrayList<>(Arrays.asList(randomValidator(), randomValidator(), randomValidator()));
    List<UnsignedLong> balanceList =
        new ArrayList<>(
            Arrays.asList(randomUnsignedLong(), randomUnsignedLong(), randomUnsignedLong()));

    if (addToList) {
      validatorList.add(knownValidator);
      balanceList.add(amount);
    }

    beaconState.setValidator_registry(validatorList);
    beaconState.setValidator_balances(balanceList);
    return beaconState;
  }

  // *************** START Shuffling Tests ***************

  @Test
  void succeedsWhenARandomShufflingIsAPermutation() {
    Bytes32 seed = Bytes32.random();
    int listSize = 1000;
    boolean[] done = new boolean[listSize]; // Initialised to false
    for (int i = 0; i < listSize; i++) {
      int idx = (int) BeaconStateUtil.get_permuted_index(i, listSize, seed);
      assertFalse(done[idx]);
      done[idx] = true;
    }
  }

  @Test
  void succeedsWhenResultMatchesTestDataForOneHundredElements() {
    // TODO: this is from protolambda's test data based on SHA256 - to be updated
    Bytes32 seed =
        Bytes32.fromHexString("0xdf3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119");
    long listSize = 100;
    int[] shuffling = {
      3, 61, 89, 23, 54, 47, 20, 58, 68, 95, 31, 4, 46, 55, 98, 2, 67, 15, 8, 19, 72, 56, 79, 64,
      96, 45, 42, 71, 22, 87, 6, 29, 70, 53, 24, 5, 41, 81, 59, 90, 86, 10, 51, 83, 44, 91, 26, 97,
      9, 85, 36, 21, 88, 18, 94, 0, 14, 82, 30, 65, 78, 28, 63, 92, 12, 76, 84, 25, 52, 33, 49, 50,
      7, 40, 35, 77, 62, 27, 38, 73, 11, 17, 99, 75, 32, 43, 74, 60, 48, 16, 13, 69, 80, 34, 93, 39,
      1, 37, 57, 66
    };
    for (int i = 0; i < listSize; i++) {
      int idx = (int) BeaconStateUtil.get_permuted_index(i, listSize, seed);
      assertEquals(shuffling[i], idx);
    }
  }

  // *************** END Shuffling Tests *****************
}
