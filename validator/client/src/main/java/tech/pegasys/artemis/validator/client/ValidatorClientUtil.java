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

package tech.pegasys.artemis.validator.client;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_of_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_crosslink_committee;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_start_shard;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.PublicKey;

public class ValidatorClientUtil {

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index`` and
   * ``registry_change``. ``assignment`` returned is a tuple of the following form: *
   * ``assignment[0]`` is the list of validators in the committee * ``assignment[1]`` is the shard
   * to which the committee is assigned * ``assignment[2]`` is the slot at which the committee is
   * assigned * ``assignment[3]`` is a bool signalling if the validator is expected to propose a
   * beacon block at the assigned slot.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validator_index the validator that is calling this function.
   * @return Optional.of(CommitteeAssignmentTuple) or Optional.empty.
   */
  public static Optional<Triple<List<Integer>, UnsignedLong, UnsignedLong>>
      get_committee_assignment(BeaconState state, UnsignedLong epoch, int validator_index) {
    UnsignedLong next_epoch = get_current_epoch(state).plus(UnsignedLong.ONE);
    checkArgument(
        epoch.compareTo(next_epoch) <= 0, "get_committe_assignment: Epoch number too high");

    int committees_per_slot =
        get_committee_count(state, epoch)
            .dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
            .intValue();
    UnsignedLong start_slot = compute_start_slot_of_epoch(epoch);

    for (UnsignedLong slot = start_slot;
        slot.compareTo(start_slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH))) < 0;
        slot = slot.plus(UnsignedLong.ONE)) {

      int offset = committees_per_slot * slot.mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).intValue();
      int slot_start_shard = (get_start_shard(state, epoch).intValue() + offset) % SHARD_COUNT;

      for (int i = 0; i < committees_per_slot; i++) {
        UnsignedLong shard = UnsignedLong.valueOf((slot_start_shard + i) % SHARD_COUNT);
        List<Integer> committee = get_crosslink_committee(state, epoch, shard);
        if (committee.contains(validator_index)) {
          return Optional.of(new ImmutableTriple<>(committee, shard, slot));
        }
      }
    }
    return Optional.empty();
  }

  public static boolean is_proposer(BeaconState state, int validator_index) {
    return get_beacon_proposer_index(state) == validator_index;
  }

  public static Bytes blsSignatureHelper(
      KeyPair blsKeys, Bytes32 withdrawal_credentials, long amount) {
    Bytes deposit_data =
        Bytes.wrap(
            Bytes.ofUnsignedLong(amount),
            withdrawal_credentials,
            getPublicKeyFromKeyPair(blsKeys).toBytesCompressed());
    return generateProofOfPossession(blsKeys, deposit_data);
  }

  public static Bytes generateProofOfPossession(KeyPair blsKeys, Bytes deposit_data) {
    return BLS12381
        .sign(blsKeys, deposit_data, Constants.DOMAIN_DEPOSIT.getWrappedBytes())
        .signature()
        .toBytesCompressed();
  }

  public static PublicKey getPublicKeyFromKeyPair(KeyPair blsKeys) {
    return BLSPublicKey.fromBytesCompressed(blsKeys.publicKey().toBytesCompressed()).getPublicKey();
  }

  public static void registerValidatorEth1(
      Validator validator, long amount, String address, Web3j web3j, DefaultGasProvider gasProvider, BLSSignature sig)
      throws Exception {
    Credentials credentials =
        Credentials.create(validator.getSecpKeys().secretKey().bytes().toHexString());
    DepositContract contract = null;
    Bytes blsSignature =
        blsSignatureHelper(validator.getBlsKeys(), validator.getWithdrawal_credentials(), amount);
    contract = DepositContract.load(address, web3j, credentials, gasProvider);

    contract
        .deposit(
            validator.getBlsKeys().publicKey().toBytesCompressed().reverse().toArray(),
            validator.getWithdrawal_credentials().reverse().toArray(),
            sig.toBytes().reverse().toArray(),
            new BigInteger(amount + "000000000"))
        .send();
  }
}
