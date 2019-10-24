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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.LocalMessageHandler;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricsSystem;

public class GoodbyeMessageHandler implements LocalMessageHandler<GoodbyeMessage, Void> {

  private final ALogger LOG = new ALogger(GoodbyeMessageHandler.class.getName());
  private final LabelledMetric<Counter> goodbyeCounter;

  public GoodbyeMessageHandler(final MetricsSystem metricsSystem) {
    goodbyeCounter =
        metricsSystem.createLabelledCounter(
            ArtemisMetricCategory.NETWORK,
            "peer_goodbye_total",
            "Total number of goodbye messages received from peers",
            "reason");
  }

  @Override
  public Void onIncomingMessage(final Peer peer, final GoodbyeMessage message) {
    LOG.log(Level.DEBUG, "Peer " + peer.getRemoteId() + " said goodbye.");
    goodbyeCounter.labels(labelForReason(message.getReason())).inc();
    return null;
  }

  private String labelForReason(final UnsignedLong reason) {
    if (GoodbyeMessage.REASON_CLIENT_SHUT_DOWN.equals(reason)) {
      return "SHUTDOWN";
    } else if (GoodbyeMessage.REASON_IRRELEVANT_NETWORK.equals(reason)) {
      return "IRRELEVANT_NETWORK";
    } else if (GoodbyeMessage.REASON_FAULT_ERROR.equals(reason)) {
      return "FAULT";
    }
    return "UNKNOWN";
  }
}
