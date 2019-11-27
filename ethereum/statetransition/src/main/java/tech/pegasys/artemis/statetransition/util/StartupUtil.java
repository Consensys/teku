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

import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.alogger.ALogger.Color;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

public final class StartupUtil {
  public static ArrayList<DepositWithIndex> newDeposits(int numDeposits) {
    ArrayList<DepositWithIndex> deposits = new ArrayList<>();

    for (int i = 0; i < numDeposits; i++) {
      // https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/validator/0_beacon-chain-validator.md#submit-deposit
      BLSKeyPair keypair = BLSKeyPair.random(i);
      DepositData depositData =
          new DepositData(
              keypair.getPublicKey(),
              Bytes32.ZERO,
              UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
              BLSSignature.empty());
      BLSSignature proof_of_possession =
          BLSSignature.sign(
              keypair,
              depositData.signing_root("signature"),
              BeaconStateUtil.compute_domain(Constants.DOMAIN_DEPOSIT));
      depositData.setSignature(proof_of_possession);

      SSZVector<Bytes32> proof =
          new SSZVector<>(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, Bytes32.ZERO);
      DepositWithIndex deposit = new DepositWithIndex(proof, depositData, UnsignedLong.valueOf(i));
      deposits.add(deposit);
    }
    return deposits;
  }

  public static Eth1Data get_eth1_data_stub(BeaconState state, UnsignedLong current_epoch) {
    UnsignedLong epochs_per_period =
        UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD)
            .dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong voting_period = current_epoch.dividedBy(epochs_per_period);
    return new Eth1Data(
        Hash.sha2_256(SSZ.encodeUInt64(epochs_per_period.longValue())),
        state.getEth1_deposit_index(),
        Hash.sha2_256(Hash.sha2_256(SSZ.encodeUInt64(voting_period.longValue()))));
  }

  private static BeaconStateWithCache createMockedStartInitialBeaconState(
      final long genesisTime, List<BLSKeyPair> validatorKeys) {
    STDOUT.log(
        Level.INFO,
        "Starting with mocked start interoperability mode with genesis time "
            + genesisTime
            + " and "
            + validatorKeys.size()
            + " validators",
        Color.GREEN);
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator().createDeposits(validatorKeys);
    return new MockStartBeaconStateGenerator()
        .createInitialBeaconState(UnsignedLong.valueOf(genesisTime), initialDepositData);
  }

  public static BeaconStateWithCache loadBeaconStateFromFile(final String stateFile)
      throws IOException {
    return BeaconStateWithCache.fromBeaconState(
        SimpleOffsetSerializer.deserialize(
            Bytes.wrap(Files.readAllBytes(new File(stateFile).toPath())), BeaconState.class));
  }

  public static void setupInitialState(
      final ChainStorageClient chainStorageClient,
      final long genesisTime,
      final String startState,
      final int numValidators) {
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numValidators);
    setupInitialState(chainStorageClient, genesisTime, startState, validatorKeys);
  }

  public static void setupInitialState(
      final ChainStorageClient chainStorageClient,
      final long genesisTime,
      final String startState,
      final List<BLSKeyPair> validatorKeyPairs) {
    chainStorageClient.setGenesisTime(UnsignedLong.valueOf(genesisTime));
    BeaconStateWithCache initialState;
    if (startState != null) {
      try {
        STDOUT.log(Level.INFO, "Loading initial state from " + startState, ALogger.Color.GREEN);
        initialState = StartupUtil.loadBeaconStateFromFile(startState);
      } catch (final IOException e) {
        throw new IllegalStateException("Failed to load initial state", e);
      }
    } else {
      initialState =
          StartupUtil.createMockedStartInitialBeaconState(genesisTime, validatorKeyPairs);
    }

    final Store store = Store.get_genesis_store(initialState);
    chainStorageClient.setStore(store);
    Bytes32 headBlockRoot = get_head(store);
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    chainStorageClient.updateBestBlock(headBlockRoot, headBlock.getSlot());
  }
}
