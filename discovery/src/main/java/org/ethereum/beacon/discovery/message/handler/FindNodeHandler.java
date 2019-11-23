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

package org.ethereum.beacon.discovery.message.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.NodeBucket;

public class FindNodeHandler implements MessageHandler<FindNodeMessage> {
  private static final Logger logger = LogManager.getLogger(FindNodeHandler.class);
  /**
   * The maximum size of any packet is 1280 bytes. Implementations should not generate or process
   * packets larger than this size. As per specification the maximum size of an ENR is 300 bytes. A
   * NODES message containing all FINDNODE response records would be at least 4800 bytes, not
   * including additional data such as the header. To stay below the size limit, NODES responses are
   * sent as multiple messages and specify the total number of responses in the message. 4х300 =
   * 1200 and we always have 80 bytes for everything else.
   */
  private static final int MAX_NODES_PER_MESSAGE = 4;

  public FindNodeHandler() {}

  @Override
  public void handle(FindNodeMessage message, NodeSession session) {
    Optional<NodeBucket> nodeBucketOptional = session.getBucket(message.getDistance());
    List<List<NodeRecord>> nodeRecordsList = new ArrayList<>();
    int total = 0;

    // Repack to lists of MAX_NODES_PER_MESSAGE size
    List<NodeRecordInfo> bucketRecords =
        nodeBucketOptional.isPresent()
            ? nodeBucketOptional.get().getNodeRecords()
            : Collections.emptyList();
    for (NodeRecordInfo nodeRecordInfo : bucketRecords) {
      if (total % MAX_NODES_PER_MESSAGE == 0) {
        nodeRecordsList.add(new ArrayList<>());
      }
      List<NodeRecord> currentList = nodeRecordsList.get(nodeRecordsList.size() - 1);
      currentList.add(nodeRecordInfo.getNode());
      ++total;
    }
    logger.trace(
        () ->
            String.format(
                "Sending %s nodes in reply to request with distance %s in session %s",
                bucketRecords.size(), message.getDistance(), session));

    // Send
    if (nodeRecordsList.isEmpty()) {
      nodeRecordsList.add(Collections.emptyList());
    }
    int finalTotal = total;
    nodeRecordsList.forEach(
        recordsList ->
            session.sendOutgoing(
                MessagePacket.create(
                    session.getHomeNodeId(),
                    session.getNodeRecord().getNodeId(),
                    session.getAuthTag().get(),
                    session.getInitiatorKey(),
                    DiscoveryV5Message.from(
                        new NodesMessage(
                            message.getRequestId(),
                            finalTotal,
                            () -> recordsList,
                            recordsList.size())))));
  }
}
