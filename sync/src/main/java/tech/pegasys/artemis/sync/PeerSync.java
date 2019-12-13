package tech.pegasys.artemis.sync;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerStatus;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseStream;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.config.Constants;

import java.util.concurrent.CompletableFuture;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.util.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;

public class PeerSync {

  private static final UnsignedLong STEP = UnsignedLong.ONE;

  private Eth2Peer peer;
  private final UnsignedLong advertisedFinalizedEpoch;
  private final UnsignedLong advertisedHeadBlockSlot;
  private final Bytes32 advertisedHeadBlockRoot;
  private final ChainStorageClient storageClient;
  private final ResponseStream.ResponseListener<BeaconBlock> blockResponseListener;

  private UnsignedLong latestRequestedSlot;

  public PeerSync(
          final Eth2Peer peer,
          final ChainStorageClient storageClient,
          final ResponseStream.ResponseListener<BeaconBlock> blockResponseListener) {
    this.peer = peer;
    this.storageClient = storageClient;
    this.blockResponseListener = blockResponseListener;

    this.advertisedFinalizedEpoch = peer.getStatus().getFinalizedEpoch();
    this.advertisedHeadBlockSlot = peer.getStatus().getHeadSlot();
    this.advertisedHeadBlockRoot = peer.getStatus().getHeadRoot();
    this.latestRequestedSlot = compute_start_slot_at_epoch(storageClient.getFinalizedEpoch());
  }

  public CompletableFuture<PeerSyncResult> sync() {
    while (latestRequestedSlot.compareTo(advertisedHeadBlockSlot) < 0) {
      requestSyncBlocks(peer, blockResponseListener).join();
    }

    if (storageClient.getFinalizedEpoch().compareTo(advertisedFinalizedEpoch) < 0) {
      return CompletableFuture.completedFuture(PeerSyncResult.FAULTY_ADVERTISEMENT);
    } else {
      return CompletableFuture.completedFuture(PeerSyncResult.SUCCESSFUL_SYNC);
    }

  }

  private CompletableFuture<Void> requestSyncBlocks(
          Eth2Peer peer, ResponseStream.ResponseListener<BeaconBlock> blockResponseListener) {
    UnsignedLong diff = advertisedHeadBlockSlot.minus(latestRequestedSlot);
    UnsignedLong count = diff.compareTo(MAX_BLOCK_BY_RANGE_REQUEST_SIZE) > 0 ? MAX_BLOCK_BY_RANGE_REQUEST_SIZE : diff;
    latestRequestedSlot = latestRequestedSlot.plus(count);
    return peer.requestBlocksByRange(advertisedHeadBlockRoot, latestRequestedSlot, count, STEP, blockResponseListener);
  }
}
