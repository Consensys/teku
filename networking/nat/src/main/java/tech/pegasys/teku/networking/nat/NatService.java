/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.nat;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class NatService extends Service {
  protected static final Logger LOG = LogManager.getLogger();

  private final Optional<NatManager> maybeNatManager;
  private final boolean isDiscoveryEnabled;
  private final int p2pPort;

  public NatService(
      final NatConfiguration natConfiguration,
      final int p2pPort,
      final boolean isDiscoveryEnabled) {
    final NatMethod currentNatMethod = natConfiguration.getNatMethod();
    this.p2pPort = p2pPort;
    this.isDiscoveryEnabled = isDiscoveryEnabled;

    if (currentNatMethod.equals(NatMethod.UPNP)) {
      maybeNatManager = Optional.of(new NatManager(natConfiguration.getNatMethod()));
    } else {
      maybeNatManager = Optional.empty();
    }
  }

  @Override
  protected SafeFuture<Void> doStart() {
    if (maybeNatManager.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final NatManager natManager = maybeNatManager.get();
    return natManager
        .start()
        .thenRun(
            () -> {
              natManager.requestPortForward(p2pPort, NetworkProtocol.TCP, NatServiceType.P2P);
              if (isDiscoveryEnabled) {
                natManager.requestPortForward(
                    p2pPort, NetworkProtocol.UDP, NatServiceType.DISCOVERY);
              }
            });
  }

  @Override
  protected SafeFuture<?> doStop() {
    if (maybeNatManager.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final NatManager natManager = maybeNatManager.get();
    return natManager.stop();
  }
}
