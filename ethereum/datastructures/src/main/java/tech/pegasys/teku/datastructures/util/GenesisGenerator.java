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

package tech.pegasys.teku.datastructures.util;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.process_deposit_without_checking_merkle_proof;
import static tech.pegasys.teku.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_FORK_VERSION;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

public class GenesisGenerator {

  private static final Logger LOG = LogManager.getLogger();

  private final MutableBeaconState state = MutableBeaconState.createBuilder();
  private final Map<BLSPublicKey, Integer> keyCache = new HashMap<>();
  private final long depositListLength = ((long) 1) << DEPOSIT_CONTRACT_TREE_DEPTH;
  private final SSZMutableList<DepositData> depositDataList =
      SSZList.createMutable(DepositData.class, depositListLength);

  private int activeValidatorCount = 0;

  public GenesisGenerator() {
    Bytes32 latestBlockRoot = new BeaconBlockBody().hash_tree_root();
    final UInt64 genesisSlot = UInt64.valueOf(Constants.GENESIS_SLOT);
    BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(
            genesisSlot, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, latestBlockRoot);
    state.setLatest_block_header(beaconBlockHeader);
    state.setFork(
        new Fork(GENESIS_FORK_VERSION, GENESIS_FORK_VERSION, UInt64.valueOf(GENESIS_EPOCH)));
  }

  public void updateCandidateState(
      Bytes32 eth1BlockHash, UInt64 eth1Timestamp, List<? extends Deposit> deposits) {
    updateGenesisTime(eth1Timestamp);

    state.setEth1_data(
        new Eth1Data(
            Bytes32.ZERO, UInt64.valueOf(depositDataList.size() + deposits.size()), eth1BlockHash));

    // Process deposits
    deposits.forEach(
        deposit -> {
          LOG.trace("About to process deposit: {}", depositDataList::size);
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
    if (validator.getActivation_epoch().equals(UInt64.valueOf(GENESIS_EPOCH))) {
      // Validator is already activated (and thus already has the max effective balance)
      return;
    }
    UInt64 balance = state.getBalances().get(index);
    UInt64 effective_balance =
        balance.minus(balance.mod(EFFECTIVE_BALANCE_INCREMENT)).min(MAX_EFFECTIVE_BALANCE);

    UInt64 activation_eligibility_epoch = validator.getActivation_eligibility_epoch();
    UInt64 activation_epoch = validator.getActivation_epoch();

    if (effective_balance.equals(MAX_EFFECTIVE_BALANCE)) {
      activation_eligibility_epoch = UInt64.valueOf(GENESIS_EPOCH);
      activation_epoch = UInt64.valueOf(GENESIS_EPOCH);
      activeValidatorCount++;
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

  public int getActiveValidatorCount() {
    return activeValidatorCount;
  }

  public UInt64 getGenesisTime() {
    return state.getGenesis_time();
  }

  public BeaconState getGenesisState() {
    finalizeState();
    return state.commitChanges();
  }

  public long getDepositCount() {
    return depositDataList.size();
  }

  private void finalizeState() {
    calculateRandaoMixes();
    calculateDepositRoot();
    final BeaconState readOnlyState = state.commitChanges();
    state.setGenesis_validators_root(readOnlyState.getValidators().hash_tree_root());
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

  private void updateGenesisTime(final UInt64 eth1Timestamp) {
    UInt64 genesisTime = eth1Timestamp.plus(Constants.GENESIS_DELAY);
    state.setGenesis_time(genesisTime);
  }
}
