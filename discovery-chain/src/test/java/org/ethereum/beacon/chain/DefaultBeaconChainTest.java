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

package org.ethereum.beacon.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.stream.IntStream;
import org.ethereum.beacon.chain.MutableBeaconChain.ImportResult;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.chain.storage.impl.SSZBeaconChainStorageFactory;
import org.ethereum.beacon.chain.storage.impl.SerializerFactory;
import org.ethereum.beacon.chain.storage.util.StorageUtils;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.BlockTransition;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.consensus.StateTransition;
import org.ethereum.beacon.consensus.transition.BeaconStateExImpl;
import org.ethereum.beacon.consensus.transition.EmptySlotTransition;
import org.ethereum.beacon.consensus.transition.ExtendedSlotTransition;
import org.ethereum.beacon.consensus.transition.InitialStateTransition;
import org.ethereum.beacon.consensus.transition.PerEpochTransition;
import org.ethereum.beacon.consensus.util.StateTransitionTestUtil;
import org.ethereum.beacon.consensus.verifier.BeaconBlockVerifier;
import org.ethereum.beacon.consensus.verifier.BeaconStateVerifier;
import org.ethereum.beacon.consensus.verifier.VerificationResult;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public class DefaultBeaconChainTest {

  @Test
  public void insertAChain() {
    Schedulers schedulers = Schedulers.createDefault();

    BeaconChainSpec spec =
        BeaconChainSpec.Builder.createWithDefaultParams()
            .withComputableGenesisTime(false)
            .withVerifyDepositProof(false)
            .build();
    StateTransition<BeaconStateEx> perSlotTransition =
        StateTransitionTestUtil.createNextSlotTransition();
    MutableBeaconChain beaconChain = createBeaconChain(spec, perSlotTransition, schedulers);

    beaconChain.init();
    BeaconTuple initialTuple = beaconChain.getRecentlyProcessed();
    assertEquals(spec.getConstants().getGenesisSlot(), initialTuple.getBlock().getSlot());

    IntStream.range(0, 10)
        .forEach(
            (idx) -> {
              BeaconTuple recentlyProcessed = beaconChain.getRecentlyProcessed();
              BeaconBlock aBlock =
                  createBlock(
                      recentlyProcessed, spec, schedulers.getCurrentTime(), perSlotTransition);
              assertEquals(ImportResult.OK, beaconChain.insert(aBlock));
              assertEquals(aBlock, beaconChain.getRecentlyProcessed().getBlock());

              System.out.println("Inserted block: " + (idx + 1));
            });
  }

  private BeaconBlock createBlock(
      BeaconTuple parent,
      BeaconChainSpec spec,
      long currentTime,
      StateTransition<BeaconStateEx> perSlotTransition) {
    BeaconBlock block =
        new BeaconBlock(
            spec.get_current_slot(parent.getState(), currentTime),
            spec.signing_root(parent.getBlock()),
            Hash32.ZERO,
            BeaconBlockBody.getEmpty(spec.getConstants()),
            BLSSignature.ZERO);
    BeaconState state = perSlotTransition.apply(new BeaconStateExImpl(parent.getState()));

    return block.withStateRoot(spec.hash_tree_root(state));
  }

  private MutableBeaconChain createBeaconChain(
      BeaconChainSpec spec,
      StateTransition<BeaconStateEx> perSlotTransition,
      Schedulers schedulers) {
    Time start = Time.castFrom(UInt64.valueOf(schedulers.getCurrentTime() / 1000));
    ChainStart chainStart = new ChainStart(start, Eth1Data.EMPTY, Collections.emptyList());
    InitialStateTransition initialTransition = new InitialStateTransition(chainStart, spec);
    BlockTransition<BeaconStateEx> perBlockTransition =
        StateTransitionTestUtil.createPerBlockTransition();
    StateTransition<BeaconStateEx> perEpochTransition =
        StateTransitionTestUtil.createStateWithNoTransition();

    BeaconBlockVerifier blockVerifier = (block, state) -> VerificationResult.PASSED;
    BeaconStateVerifier stateVerifier = (block, state) -> VerificationResult.PASSED;
    Database database = Database.inMemoryDB();
    BeaconChainStorage chainStorage =
        new SSZBeaconChainStorageFactory(
                spec.getObjectHasher(), SerializerFactory.createSSZ(spec.getConstants()))
            .create(database);
    BeaconStateEx initialState = initialTransition.apply(spec.get_empty_block());
    StorageUtils.initializeStorage(chainStorage, spec, initialState);

    return new DefaultBeaconChain(
        spec,
        new EmptySlotTransition(
            new ExtendedSlotTransition(
                new PerEpochTransition(spec) {
                  @Override
                  public BeaconStateEx apply(BeaconStateEx stateEx) {
                    return perEpochTransition.apply(stateEx);
                  }
                },
                perSlotTransition,
                spec)),
        perBlockTransition,
        blockVerifier,
        stateVerifier,
        chainStorage,
        schedulers);
  }
}
