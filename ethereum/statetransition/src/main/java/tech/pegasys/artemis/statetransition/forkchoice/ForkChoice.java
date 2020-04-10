package tech.pegasys.artemis.statetransition.forkchoice;

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
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.artemis.storage.client.RecentChainData;

import static tech.pegasys.artemis.statetransition.forkchoice.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.statetransition.forkchoice.ForkChoiceUtil.on_block;

public class ForkChoice implements FinalizedCheckpointChannel {

  private final ProtoArrayForkChoice protoArrayForkChoice;
  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;

  public ForkChoice(final ProtoArrayForkChoice protoArrayForkChoice,
                    final RecentChainData recentChainData,
                    final StateTransition stateTransition) {
    this.protoArrayForkChoice = protoArrayForkChoice;
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
  }

  public Bytes32 processHead() {
    Store store = recentChainData.getStore();
    Checkpoint justifiedCheckpoint = store.getJustifiedCheckpoint();
    Bytes32 headBlockRoot = protoArrayForkChoice.findHead(
            justifiedCheckpoint.getEpoch(),
            justifiedCheckpoint.getRoot(),
            store.getFinalizedCheckpoint().getEpoch(),
            store.getCheckpointState(justifiedCheckpoint).getBalances().asList()
    );
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    recentChainData.updateBestBlock(headBlockRoot, headBlock.getSlot());
    return headBlockRoot;
  }

  public BlockImportResult onBlock(final MutableStore store,
                                   final SignedBeaconBlock block) {
    return on_block(store, block, stateTransition, protoArrayForkChoice);
  }

  public AttestationProcessingResult onAttestation(final MutableStore store,
                                                   final Attestation attestation) {
    return on_attestation(store, attestation, stateTransition, protoArrayForkChoice);
  }

  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    protoArrayForkChoice.maybePrune(checkpoint.getRoot());
  }
}
