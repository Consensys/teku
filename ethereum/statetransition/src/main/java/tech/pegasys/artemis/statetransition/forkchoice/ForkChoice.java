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

package tech.pegasys.artemis.statetransition.forkchoice;

import static tech.pegasys.artemis.statetransition.forkchoice.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.statetransition.forkchoice.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.results.AttestationProcessingResult;
import tech.pegasys.artemis.core.results.BlockImportResult;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.forkchoice.MutableStore;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.protoarray.ProtoArrayForkChoice;
import tech.pegasys.artemis.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class ForkChoice implements FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;
  private final block stateTransition;

  private ProtoArrayForkChoice protoArrayForkChoice;

  public ForkChoice(final RecentChainData recentChainData,
                    final StateTransition stateTransition,
                    final EventBus eventBus) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
    eventBus.register(this);
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockProposed(final ProposedBlockEvent blockProposedEvent) {
    LOG.trace("Preparing to import proposed block: {}", blockProposedEvent.getBlock());
    final BlockImportResult result = blockImporter.importBlock(blockProposedEvent.getBlock());
    if (result.isSuccessful()) {
      LOG.trace("Successfully imported proposed block: {}", blockProposedEvent.getBlock());
    } else {
      LOG.error(
              "Failed to import proposed block for reason + "
                      + result.getFailureReason()
                      + ": "
                      + blockProposedEvent,
              result.getFailureCause().orElse(null));
    }
  }

  private void initializeProtoArrayForkChoice() {
    System.out.println("initializinig proto array fork choice");
    protoArrayForkChoice = ProtoArrayForkChoice.create(recentChainData.getStore());
  }

  public Bytes32 processHead() {
    Store store = recentChainData.getStore();
    Checkpoint justifiedCheckpoint = store.getJustifiedCheckpoint();
    Bytes32 headBlockRoot =
        protoArrayForkChoice.findHead(
            justifiedCheckpoint.getEpoch(),
            justifiedCheckpoint.getRoot(),
            store.getFinalizedCheckpoint().getEpoch(),
            store.getCheckpointState(justifiedCheckpoint).getBalances().asList());
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    recentChainData.updateBestBlock(headBlockRoot, headBlock.getSlot());
    return headBlockRoot;
  }

  public BlockImportResult onBlock(final MutableStore store, final SignedBeaconBlock block) {
    return on_block(store, block, stateTransition, protoArrayForkChoice);
  }

  public AttestationProcessingResult onAttestation(
      final MutableStore store, final Attestation attestation) {
    return on_attestation(store, attestation, stateTransition, protoArrayForkChoice);
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    protoArrayForkChoice.maybePrune(checkpoint.getRoot());
  }
}
