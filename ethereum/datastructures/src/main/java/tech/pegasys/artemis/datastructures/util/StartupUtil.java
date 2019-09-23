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

import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.alogger.ALogger.Color;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public final class StartupUtil {
  private static final ALogger STDOUT = new ALogger("stdout");

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

  public static BeaconBlock newBeaconBlock(
      BeaconState state,
      Bytes32 previous_block_root,
      Bytes32 state_root,
      SSZList<Deposit> deposits,
      SSZList<Attestation> attestations,
      int numValidators,
      boolean interopActive) {
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    if (interopActive) {
      beaconBlockBody.setEth1_data(get_eth1_data_stub(state, compute_epoch_of_slot(slot)));
    } else {
      beaconBlockBody.setEth1_data(
          new Eth1Data(
              Constants.ZERO_HASH, UnsignedLong.valueOf(numValidators), Constants.ZERO_HASH));
    }
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    return new BeaconBlock(
        slot, previous_block_root, state_root, beaconBlockBody, BLSSignature.empty());
  }

  private static Eth1Data get_eth1_data_stub(BeaconState state, UnsignedLong current_epoch) {
    UnsignedLong epochs_per_period =
        UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD)
            .dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong voting_period = current_epoch.dividedBy(epochs_per_period);
    return new Eth1Data(
        Hash.sha2_256(SSZ.encodeUInt64(epochs_per_period.longValue())),
        state.getEth1_deposit_index(),
        Hash.sha2_256(Hash.sha2_256(SSZ.encodeUInt64(voting_period.longValue()))));
  }

  public static BeaconStateWithCache createInitialBeaconState(ArtemisConfiguration config) {
    BeaconStateWithCache initialState;
    UnsignedLong genesisTime = UnsignedLong.valueOf(config.getGenesisTime());
    if (config.getInteropActive()) {
      initialState = createMockedStartInitialBeaconState(config);
    } else {
      initialState =
          BeaconStateUtil.initialize_beacon_state_from_eth1_new(
              Bytes32.ZERO,
              UnsignedLong.valueOf(config.getGenesisTime()),
              newDeposits(config.getNumValidators()));
    }
    initialState.setGenesis_time(genesisTime);
    return initialState;
  }

  private static BeaconStateWithCache createMockedStartInitialBeaconState(
      final ArtemisConfiguration config) {
    final UnsignedLong genesisTime = UnsignedLong.valueOf(config.getGenesisTime());
    final int validatorCount = config.getNumValidators();
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount - 1);
    STDOUT.log(
        Level.INFO,
        "Using mocked start interoperability mode with genesis time "
            + genesisTime
            + " and "
            + validatorCount
            + " validators",
        Color.GREEN);
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator().createDeposits(validatorKeys);
    return new MockStartBeaconStateGenerator()
        .createInitialBeaconState(genesisTime, initialDepositData);
  }
}
