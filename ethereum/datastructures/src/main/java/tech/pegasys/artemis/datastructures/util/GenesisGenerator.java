/*
 * Copyright 2020 ConsenSys AG.
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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.process_deposit_without_checking_merkle_proof;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_FORK_VERSION;
import static tech.pegasys.artemis.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_DELAY;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class GenesisGenerator {

  private static final Logger LOG = LogManager.getLogger();

  private final MutableBeaconState state = MutableBeaconState.createBuilder();
  private final Map<BLSPublicKey, Integer> keyCache = new HashMap<>();
  private final long depositListLength = ((long) 1) << DEPOSIT_CONTRACT_TREE_DEPTH;
  private final SSZMutableList<DepositData> depositDataList =
      SSZList.createMutable(DepositData.class, depositListLength);

  public GenesisGenerator() {
    Bytes32 latestBlockRoot = new BeaconBlockBody().hash_tree_root();
    BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(UnsignedLong.ZERO, Bytes32.ZERO, Bytes32.ZERO, latestBlockRoot);
    state.setLatest_block_header(beaconBlockHeader);
    state.setFork(
        new Fork(GENESIS_FORK_VERSION, GENESIS_FORK_VERSION, UnsignedLong.valueOf(GENESIS_EPOCH)));
  }

  public void updateCandidateState(
      Bytes32 eth1BlockHash, UnsignedLong eth1Timestamp, List<? extends Deposit> deposits) {
    updateGenesisTime(eth1Timestamp);

    state.setEth1_data(
        new Eth1Data(
            Bytes32.ZERO,
            UnsignedLong.valueOf(depositDataList.size() + deposits.size()),
            eth1BlockHash));

    // Process deposits
    deposits.forEach(
        deposit -> {
          LOG.debug("About to process deposit: " + depositDataList.size());
          depositDataList.add(deposit.getData());

          // Skip verifying the merkle proof as these deposits come directly from an Eth1 event.
          // We do still verify the signature
          process_deposit_without_checking_merkle_proof(state, deposit, keyCache);

          processActivation(deposit);
        });
  }

  private void processActivation(final Deposit deposit) {
    final Integer index = keyCache.get(deposit.getData().getPubkey());
    if (index == null) {
      // Could be null if the deposit was invalid
      return;
    }
    Validator validator = state.getValidators().get(index);
    UnsignedLong balance = state.getBalances().get(index);
    UnsignedLong effective_balance =
        BeaconStateUtil.min(
            balance.minus(balance.mod(UnsignedLong.valueOf(EFFECTIVE_BALANCE_INCREMENT))),
            UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE));

    UnsignedLong activation_eligibility_epoch = validator.getActivation_eligibility_epoch();
    UnsignedLong activation_epoch = validator.getActivation_epoch();

    if (validator.getEffective_balance().equals(UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))) {
      activation_eligibility_epoch = UnsignedLong.valueOf(GENESIS_EPOCH);
      activation_epoch = UnsignedLong.valueOf(GENESIS_EPOCH);
    }

    Validator modifiedValidator =
        new Validator(
            validator.getPubkey(),
            validator.getWithdrawal_credentials(),
            effective_balance,
            validator.isSlashed(),
            activation_eligibility_epoch,
            activation_epoch,
            validator.getExit_epoch(),
            validator.getWithdrawable_epoch());

    state.getValidators().set(index, modifiedValidator);
  }

  public BeaconState getGenesisState() {
    return getGenesisStateIfValid(state -> true).orElseThrow();
  }

  public Optional<BeaconState> getGenesisStateIfValid(Predicate<BeaconState> validityCriteria) {
    if (!validityCriteria.test(state)) {
      return Optional.empty();
    }

    finalizeState();

    return Optional.of(state.commitChanges());
  }

  private void finalizeState() {
    calculateRandaoMixes();
    calculateDepositRoot();
  }

  private void calculateRandaoMixes() {
    for (int i = 0; i < state.getRandao_mixes().size(); i++) {
      state.getRandao_mixes().set(i, state.getEth1_data().getBlock_hash());
    }
  }

  private void calculateDepositRoot() {
    Eth1Data eth1Data = state.getEth1_data();
    state.setEth1_data(
        new Eth1Data(
            HashTreeUtil.hash_tree_root(HashTreeUtil.SSZTypes.LIST_OF_COMPOSITE, depositDataList),
            eth1Data.getDeposit_count(),
            eth1Data.getBlock_hash()));
  }

  private void updateGenesisTime(final UnsignedLong eth1Timestamp) {
    UnsignedLong genesisTime =
        eth1Timestamp
            .minus(eth1Timestamp.mod(UnsignedLong.valueOf(MIN_GENESIS_DELAY)))
            .plus(UnsignedLong.valueOf(2).times(UnsignedLong.valueOf(MIN_GENESIS_DELAY)));
    state.setGenesis_time(genesisTime);
  }
}
