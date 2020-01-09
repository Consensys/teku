package tech.pegasys.artemis.networking.eth2.discovery;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;
import com.google.common.eventbus.EventBus;

import static com.google.common.base.Preconditions.checkNotNull;

public class BusNodeTable extends DelegatingNodeTable {

  Logger logger = LogManager.getLogger();

  private final EventBus eventBus;

  public BusNodeTable(final NodeTable delegate, final EventBus eventBus) {
    super(delegate);
    checkNotNull(eventBus, "EventBus cannot be null");
    this.eventBus = eventBus;
  }

  @Override
  public void save(NodeRecordInfo node) {
    super.save(node);
    eventBus.post(new DiscoveryNewPeerResponse(node));
    logger.debug("Posted saved node:" + node);

  }

  @Override
  public List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit) {
    List<NodeRecordInfo> closestNodes = super.findClosestNodes(nodeId, logLimit);
    eventBus.post(new DiscoveryFindNodesResponse(closestNodes));
    logger.debug("Found closest nodes:" + closestNodes);
    return closestNodes;
  }
}
