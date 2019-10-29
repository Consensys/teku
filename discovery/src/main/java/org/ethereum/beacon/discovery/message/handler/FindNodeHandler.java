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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.NodeSession;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.storage.NodeBucket;

public class FindNodeHandler implements MessageHandler<FindNodeMessage> {
  private static final Logger logger = LogManager.getLogger(FindNodeHandler.class);

  public FindNodeHandler() {}

  @Override
  public void handle(FindNodeMessage message, NodeSession session) {
    List<NodeBucket> nodeBuckets =
        IntStream.range(0, message.getDistance())
            .mapToObj(session::getBucket)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    logger.trace(
        () ->
            String.format(
                "Sending %s nodeBuckets in reply to request in session %s",
                nodeBuckets.size(), session));
    nodeBuckets.forEach(
        bucket ->
            session.sendOutgoing(
                MessagePacket.create(
                    session.getHomeNodeId(),
                    session.getNodeRecord().getNodeId(),
                    session.getAuthTag().get(),
                    session.getInitiatorKey(),
                    DiscoveryV5Message.from(
                        new NodesMessage(
                            message.getRequestId(),
                            nodeBuckets.size(),
                            () ->
                                bucket.getNodeRecords().stream()
                                    .map(NodeRecordInfo::getNode)
                                    .collect(Collectors.toList()),
                            bucket.size())))));
  }
}
