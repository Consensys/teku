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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.statetransition.StateTransition.process_slots;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_genesis_store;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.PublicKey;
import org.json.simple.parser.ParseException;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconBlockUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.event.Eth2Genesis;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {

  private final EventBus eventBus;
  private Store store;
  private ChainStorageClient chainStorageClient;
  private ArtemisConfiguration config;
  private static final ALogger STDOUT = new ALogger("stdout");
  private List<Deposit> deposits;

  public StateProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    this.eventBus = config.getEventBus();
    this.config = config.getConfig();
    this.eventBus.register(this);
    this.chainStorageClient = chainStorageClient;
  }

  @Subscribe
  public void onEth2GenesisEvent(Eth2GenesisEvent event) {
    STDOUT.log(
        Level.INFO,
        "******* Eth2Genesis Event detected ******* : "
            + ((Eth2Genesis) event).getDeposit_root().toString());
    try {
      BeaconState initial_state;
      if (config.getDepositMode().equals(Constants.DEPOSIT_TEST))
        initial_state = DataStructureUtil.createInitialBeaconState(config);
      else {
        deposits = DepositUtil.generateBranchProofs(deposits);
        initial_state =
            DataStructureUtil.createInitialBeaconState(
                deposits, ((Eth2Genesis) event).getDeposit_root());
      }
      this.store = get_genesis_store(initial_state);
      chainStorageClient.setStore(store);
      Bytes32 genesisBlockRoot = get_head(store);
      STDOUT.log(Level.INFO, "Initial state root is " + initial_state.hash_tree_root().toHexString());
      STDOUT.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
    } catch (IllegalStateException | IOException | ParseException e) {
      STDOUT.log(Level.FATAL, e.toString());
    }
  }

  @Subscribe
  public void onDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    if (deposits == null) deposits = new ArrayList<Deposit>();
    deposits.add(DepositUtil.convertEventDepositToOperationDeposit(event));
  }

  @Subscribe
  private void onTick(Date date) {
    on_tick(store, UnsignedLong.valueOf(date.getTime()));
  }

  @Subscribe
  private void onBlock(BeaconBlock block) {
    on_block(store, block);
  }

  @Subscribe
  private void onAttestation(Attestation attestation) {
    on_attestation(store, attestation);
  }
}
