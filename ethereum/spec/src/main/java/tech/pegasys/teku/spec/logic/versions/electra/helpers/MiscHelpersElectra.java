/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.crypto.Hash.getSha256Instance;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class MiscHelpersElectra extends MiscHelpersDeneb {
  public static final UInt64 MAX_RANDOM_VALUE = UInt64.valueOf(65535);
  private final SpecConfigElectra specConfigElectra;
  private final PredicatesElectra predicatesElectra;

  public MiscHelpersElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final SchemaDefinitions schemaDefinitions) {
    super(
        SpecConfigDeneb.required(specConfig),
        predicates,
        SchemaDefinitionsDeneb.required(schemaDefinitions));
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
    this.predicatesElectra = PredicatesElectra.required(predicates);
  }

  public static MiscHelpersElectra required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  @Override
  public int computeProposerIndex(
      final BeaconState state, final IntList indices, final Bytes32 seed) {
    return computeProposerIndex(
        state,
        indices,
        seed,
        SpecConfigElectra.required(specConfig).getMaxEffectiveBalanceElectra());
  }

  /*
   * <spec function="compute_proposer_index" fork="electra" style="diff">
   * --- phase0
   * +++ electra
   * @@ -3,13 +3,15 @@
   *      Return from ``indices`` a random index sampled by effective balance.
   *      """
   *      assert len(indices) > 0
   * -    MAX_RANDOM_BYTE = 2**8 - 1
   * +    MAX_RANDOM_VALUE = 2**16 - 1
   *      i = uint64(0)
   *      total = uint64(len(indices))
   *      while True:
   *          candidate_index = indices[compute_shuffled_index(i % total, total, seed)]
   * -        random_byte = hash(seed + uint_to_bytes(uint64(i // 32)))[i % 32]
   * +        random_bytes = hash(seed + uint_to_bytes(i // 16))
   * +        offset = i % 16 * 2
   * +        random_value = bytes_to_uint64(random_bytes[offset:offset + 2])
   *          effective_balance = state.validators[candidate_index].effective_balance
   * -        if effective_balance * MAX_RANDOM_BYTE >= MAX_EFFECTIVE_BALANCE * random_byte:
   * +        if effective_balance * MAX_RANDOM_VALUE >= MAX_EFFECTIVE_BALANCE_ELECTRA * random_value:
   *              return candidate_index
   *          i += 1
   * </spec>
   */
  @Override
  protected int computeProposerIndex(
      final BeaconState state,
      final IntList indices,
      final Bytes32 seed,
      final UInt64 maxEffectiveBalance) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");

    final Sha256 sha256 = getSha256Instance();

    int i = 0;
    final int total = indices.size();
    Bytes randomBytes = null;
    while (true) {
      final int candidateIndex = indices.getInt(computeShuffledIndex(i % total, total, seed));
      if (i % 16 == 0) {
        randomBytes = Bytes.wrap(sha256.digest(seed, uint64ToBytes(Math.floorDiv(i, 16L))));
      }
      final int offset = (i % 16) * 2;
      final UInt64 randomValue = bytesToUInt64(randomBytes.slice(offset, 2));
      final UInt64 validatorEffectiveBalance =
          state.getValidators().get(candidateIndex).getEffectiveBalance();
      if (validatorEffectiveBalance
          .times(MAX_RANDOM_VALUE)
          .isGreaterThanOrEqualTo(maxEffectiveBalance.times(randomValue))) {
        return candidateIndex;
      }
      i++;
    }
  }

  /*
   * <spec function="get_max_effective_balance" fork="electra">
   * def get_max_effective_balance(validator: Validator) -> Gwei:
   *     """
   *     Get max effective balance for ``validator``.
   *     """
   *     if has_compounding_withdrawal_credential(validator):
   *         return MAX_EFFECTIVE_BALANCE_ELECTRA
   *     else:
   *         return MIN_ACTIVATION_BALANCE
   * </spec>
   */
  @Override
  public UInt64 getMaxEffectiveBalance(final Validator validator) {
    return predicatesElectra.hasCompoundingWithdrawalCredential(validator)
        ? specConfigElectra.getMaxEffectiveBalanceElectra()
        : specConfigElectra.getMinActivationBalance();
  }

  /*
   * <spec function="get_validator_from_deposit" fork="electra" style="diff">
   * --- phase0
   * +++ electra
   * @@ -1,13 +1,16 @@
   *  def get_validator_from_deposit(pubkey: BLSPubkey, withdrawal_credentials: Bytes32, amount: uint64) -> Validator:
   * -    effective_balance = min(amount - amount % EFFECTIVE_BALANCE_INCREMENT, MAX_EFFECTIVE_BALANCE)
   * -
   * -    return Validator(
   * +    validator = Validator(
   *          pubkey=pubkey,
   *          withdrawal_credentials=withdrawal_credentials,
   * -        effective_balance=effective_balance,
   * +        effective_balance=Gwei(0),
   *          slashed=False,
   *          activation_eligibility_epoch=FAR_FUTURE_EPOCH,
   *          activation_epoch=FAR_FUTURE_EPOCH,
   *          exit_epoch=FAR_FUTURE_EPOCH,
   *          withdrawable_epoch=FAR_FUTURE_EPOCH,
   *      )
   * +
   * +    max_effective_balance = get_max_effective_balance(validator)
   * +    validator.effective_balance = min(amount - amount % EFFECTIVE_BALANCE_INCREMENT, max_effective_balance)
   * +
   * +    return validator
   * </spec>
   */
  @Override
  public Validator getValidatorFromDeposit(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final Validator validator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            ZERO,
            false,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);

    final UInt64 maxEffectiveBalance = getMaxEffectiveBalance(validator);
    final UInt64 validatorEffectiveBalance =
        amount
            .minusMinZero(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(maxEffectiveBalance);

    return validator.withEffectiveBalance(validatorEffectiveBalance);
  }

  @Override
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    // if the next deposit to be processed by Eth1Data poll has the index of the first deposit
    // processed with the new deposit flow, i.e. `eth1_deposit_index ==
    // deposit_requests_start_index`, we should stop Eth1Data deposits processing
    return state
        .getEth1DepositIndex()
        .equals(BeaconStateElectra.required(state).getDepositRequestsStartIndex());
  }

  @Override
  public Optional<MiscHelpersElectra> toVersionElectra() {
    return Optional.of(this);
  }
}
