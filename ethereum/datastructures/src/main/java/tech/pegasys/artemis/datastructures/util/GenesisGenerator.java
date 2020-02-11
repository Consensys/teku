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
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_FORK_VERSION;
import static tech.pegasys.artemis.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_DAY;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class GenesisGenerator {
  private final MerkleTree depositMerkleTree = new OptimizedMerkleTree(DEPOSIT_CONTRACT_TREE_DEPTH);
  private final BeaconState state = new BeaconState();
  private final Map<BLSPublicKey, Integer> keyCache = new HashMap<>();
  private final long depositListLength = ((long) 1) << DEPOSIT_CONTRACT_TREE_DEPTH;
  private final SSZList<DepositData> depositDataList =
      new SSZList<>(DepositData.class, depositListLength);

  public GenesisGenerator() {
    BeaconBlockHeader beaconBlockHeader = new BeaconBlockHeader();
    Bytes32 latestBlockRoot = new BeaconBlockBody().hash_tree_root();
    beaconBlockHeader.setBody_root(latestBlockRoot);
    state.setLatest_block_header(beaconBlockHeader);
    state.setFork(
        new Fork(GENESIS_FORK_VERSION, GENESIS_FORK_VERSION, UnsignedLong.valueOf(GENESIS_EPOCH)));
  }

  public void addDepositsFromBlock(
      Bytes32 eth1BlockHash, UnsignedLong eth1Timestamp, List<? extends Deposit> deposits) {
    updateGenesisTime(eth1Timestamp);

    final Eth1Data eth1Data = state.getEth1_data();
    eth1Data.setBlock_hash(eth1BlockHash);
    eth1Data.setDeposit_count(UnsignedLong.valueOf(depositDataList.size() + deposits.size()));

    // Process deposits
    deposits.forEach(
        deposit -> {
          STDOUT.log(Level.DEBUG, "About to process deposit: " + depositDataList.size());
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
    validator.setEffective_balance(effective_balance);

    if (validator.getEffective_balance().equals(UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))) {
      validator.setActivation_eligibility_epoch(UnsignedLong.valueOf(GENESIS_EPOCH));
      validator.setActivation_epoch(UnsignedLong.valueOf(GENESIS_EPOCH));
    }
  }

  public BeaconStateWithCache getGenesisState() {
    return getGenesisStateIfValid(state -> true).orElseThrow();
  }

  public Optional<BeaconStateWithCache> getGenesisStateIfValid(
      Predicate<BeaconState> validityCriteria) {
    if (!validityCriteria.test(state)) {
      return Optional.empty();
    }

    finalizeState();
    return Optional.of(BeaconStateWithCache.deepCopy(state));
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
    state
        .getEth1_data()
        .setDeposit_root(
            HashTreeUtil.hash_tree_root(
                HashTreeUtil.SSZTypes.LIST_OF_COMPOSITE, depositListLength, depositDataList));
  }

  private void updateGenesisTime(final UnsignedLong eth1Timestamp) {
    state.setGenesis_time(
        eth1Timestamp
            .minus(eth1Timestamp.mod(UnsignedLong.valueOf(SECONDS_PER_DAY)))
            .plus(UnsignedLong.valueOf(2).times(UnsignedLong.valueOf(SECONDS_PER_DAY))));
  }
}
