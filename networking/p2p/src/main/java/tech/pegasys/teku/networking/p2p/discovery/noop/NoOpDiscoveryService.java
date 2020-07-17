/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.networking.p2p.discovery.noop;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;

public class NoOpDiscoveryService implements DiscoveryService {

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<?> stop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public Stream<DiscoveryPeer> streamKnownPeers() {
    return Stream.empty();
  }

  @Override
  public SafeFuture<Void> searchForPeers() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getDiscoveryAddress() {
    return Optional.empty();
  }

  @Override
  public void updateCustomENRField(String fieldName, Bytes value) {}
}
