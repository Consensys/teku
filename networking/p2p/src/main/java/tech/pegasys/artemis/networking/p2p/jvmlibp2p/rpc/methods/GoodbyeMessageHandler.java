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

import io.libp2p.core.Connection;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.LocalMessageHandler;
import tech.pegasys.artemis.util.alogger.ALogger;

public class GoodbyeMessageHandler implements LocalMessageHandler<GoodbyeMessage, Void> {

  private final ALogger LOG = new ALogger(GoodbyeMessageHandler.class.getName());

  @Override
  public Void invokeLocal(final Connection connection, final GoodbyeMessage message) {
    LOG.log(Level.DEBUG, "Peer " + connection.getSecureSession().getRemoteId() + " said goodbye.");
    return null;
  }
}
