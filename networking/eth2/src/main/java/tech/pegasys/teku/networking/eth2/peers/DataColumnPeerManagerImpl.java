package tech.pegasys.teku.networking.eth2.peers;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnPeerManager;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnReqResp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataColumnPeerManagerImpl implements DataColumnPeerManager, PeerConnectedSubscriber<Eth2Peer>, BatchDataColumnReqResp {

  private final Subscribers<PeerListener> listeners = Subscribers.create(true);
  private Map<UInt256, Eth2Peer> connectedPeers = new ConcurrentHashMap<>();

  @Override
  public void onConnected(Eth2Peer peer) {
    peerConnected(peer);
  }

  private void peerConnected(Eth2Peer peer) {
    UInt256 uintPeerId = nodeIdToUInt(peer.getId());
    listeners.forEach(l -> l.peerConnected(uintPeerId));
    connectedPeers.put(uintPeerId, peer);
    peer.subscribeDisconnect((__1, __2) -> peerDisconnected(peer));
  }

  private void peerDisconnected(Eth2Peer peer) {
    UInt256 uintPeerId = nodeIdToUInt(peer.getId());
    listeners.forEach(l -> l.peerDisconnected(uintPeerId));
    connectedPeers.remove(uintPeerId);
  }

  private UInt256 nodeIdToUInt(NodeId nodeId) {
    return UInt256.fromBytes(nodeId.toBytes());
  }

  @Override
  public void addPeerListener(PeerListener listener) {
    listeners.subscribe(listener);
  }

  @Override
  public void banNode(UInt256 node) {
    // TODO
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> requestDataColumnSidecar(UInt256 nodeId, List<DataColumnIdentifier> columnIdentifiers) {
    Eth2Peer eth2Peer = connectedPeers.get(nodeId);
    if (eth2Peer == null) {
      return SafeFuture.failedFuture(new DataColumnReqResp.DasPeerDisconnectedException());
    } else {
      List<DataColumnSidecar> responseCollector = new ArrayList<>();
      return eth2Peer
          .requestDataColumnSidecarsByRoot(
              columnIdentifiers,
              sidecar -> {
                responseCollector.add(sidecar);
                return SafeFuture.COMPLETE;
              })
          .thenApply(__ -> responseCollector);
    }
  }

  @Override
  public int getCurrentRequestLimit(UInt256 nodeId) {
    Eth2Peer eth2Peer = connectedPeers.get(nodeId);
    if (eth2Peer == null) {
      return 0;
    } else {
      return (int) eth2Peer.getAvailableDataColumnSidecarsRequestCount();
    }
  }
}
