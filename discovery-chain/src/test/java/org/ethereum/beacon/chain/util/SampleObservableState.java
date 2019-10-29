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

package org.ethereum.beacon.chain.util;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.ethereum.beacon.chain.DefaultBeaconChain;
import org.ethereum.beacon.chain.MutableBeaconChain;
import org.ethereum.beacon.chain.SlotTicker;
import org.ethereum.beacon.chain.observer.ObservableStateProcessor;
import org.ethereum.beacon.chain.observer.ObservableStateProcessorImpl;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.chain.storage.impl.SSZBeaconChainStorageFactory;
import org.ethereum.beacon.chain.storage.impl.SerializerFactory;
import org.ethereum.beacon.chain.storage.util.StorageUtils;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.consensus.StateTransitions;
import org.ethereum.beacon.consensus.TestUtils;
import org.ethereum.beacon.consensus.transition.EmptySlotTransition;
import org.ethereum.beacon.consensus.transition.InitialStateTransition;
import org.ethereum.beacon.consensus.transition.PerBlockTransition;
import org.ethereum.beacon.consensus.verifier.BeaconBlockVerifier;
import org.ethereum.beacon.consensus.verifier.BeaconStateVerifier;
import org.ethereum.beacon.consensus.verifier.VerificationResult;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.crypto.BLS381.KeyPair;
import org.ethereum.beacon.db.InMemoryDatabase;
import org.ethereum.beacon.schedulers.Schedulers;
import org.javatuples.Pair;
import org.reactivestreams.Publisher;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public class SampleObservableState {
  private final BeaconChainSpec spec;

  public List<Deposit> deposits;
  public List<KeyPair> depositKeys;
  public Eth1Data eth1Data;
  public ChainStart chainStart;
  public InMemoryDatabase db;
  public BeaconChainStorage beaconChainStorage;
  public MutableBeaconChain beaconChain;
  public SlotTicker slotTicker;
  public ObservableStateProcessor observableStateProcessor;

  public SampleObservableState(
      Random rnd,
      Duration genesisTime,
      long genesisSlot,
      Duration slotDuration,
      int validatorCount,
      Publisher<Attestation> attestationsSteam,
      Schedulers schedulers) {

    SpecConstants specConstants =
        new SpecConstants() {
          @Override
          public SlotNumber.EpochLength getSlotsPerEpoch() {
            return new SlotNumber.EpochLength(UInt64.valueOf(validatorCount));
          }

          @Override
          public Time getSecondsPerSlot() {
            return Time.of(slotDuration.getSeconds());
          }

          @Override
          public SlotNumber getGenesisSlot() {
            return SlotNumber.of(genesisSlot);
          }
        };
    this.spec = BeaconChainSpec.createWithoutDepositVerification(specConstants);

    Pair<List<Deposit>, List<KeyPair>> anyDeposits = TestUtils.getAnyDeposits(rnd, spec, 8);
    deposits = anyDeposits.getValue0();
    depositKeys = anyDeposits.getValue1();

    eth1Data =
        new Eth1Data(Hash32.random(rnd), UInt64.valueOf(deposits.size()), Hash32.random(rnd));
    chainStart = new ChainStart(Time.of(genesisTime.getSeconds()), eth1Data, deposits);

    InitialStateTransition initialTransition = new InitialStateTransition(chainStart, spec);
    EmptySlotTransition preBlockTransition = StateTransitions.preBlockTransition(spec);
    PerBlockTransition blockTransition = StateTransitions.blockTransition(spec);

    db = new InMemoryDatabase();
    beaconChainStorage =
        new SSZBeaconChainStorageFactory(
                spec.getObjectHasher(), SerializerFactory.createSSZ(specConstants))
            .create(db);
    BeaconStateEx initialState = initialTransition.apply(spec.get_empty_block());
    StorageUtils.initializeStorage(beaconChainStorage, spec, initialState);

    BeaconBlockVerifier blockVerifier = (block, state) -> VerificationResult.PASSED;
    BeaconStateVerifier stateVerifier = (block, state) -> VerificationResult.PASSED;

    beaconChain =
        new DefaultBeaconChain(
            spec,
            preBlockTransition,
            blockTransition,
            blockVerifier,
            stateVerifier,
            beaconChainStorage,
            schedulers);
    beaconChain.init();

    slotTicker = new SlotTicker(spec, beaconChain.getRecentlyProcessed().getState(), schedulers);
    slotTicker.start();

    observableStateProcessor =
        new ObservableStateProcessorImpl(
            beaconChainStorage,
            slotTicker.getTickerStream(),
            attestationsSteam,
            beaconChain.getBlockStatesStream(),
            spec,
            preBlockTransition,
            schedulers);
    observableStateProcessor.start();
  }
}
