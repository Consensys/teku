package tech.pegasys.artemis.networking.eth2.discovery;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;

public class DelegatingNodeTable implements NodeTable {

  private final NodeTable delegate;

  public DelegatingNodeTable(NodeTable delegate) {
    this.delegate = delegate;
  }

  @Override
  public void save(NodeRecordInfo node) {
    delegate.save(node);
  }

  @Override
  public void remove(NodeRecordInfo node) {
    delegate.remove(node);
  }

  @Override
  public Optional<NodeRecordInfo> getNode(Bytes nodeId) {
    return delegate.getNode(nodeId);
  }

  @Override
  public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
    return delegate.findClosestNodes(nodeId,logLimit);
  }

  @Override
  public NodeRecord getHomeNode() {
    return delegate.getHomeNode();
  }
}
