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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import java.util.concurrent.ConcurrentHashMap;
import net.consensys.cava.rlpx.RLPxService;
import net.consensys.cava.rlpx.wire.SubProtocol;
import net.consensys.cava.rlpx.wire.SubProtocolHandler;
import net.consensys.cava.rlpx.wire.SubProtocolIdentifier;
import tech.pegasys.artemis.data.TimeSeriesRecord;

final class HobbitsSubProtocol implements SubProtocol {

  static final SubProtocolIdentifier BEACON_ID = SubProtocolIdentifier.of("hob", 1);
  private final EventBus eventBus;
  private final String userAgent;
  private final TimeSeriesRecord chainData;
  private HobbitsSubProtocolHandler handler;
  private final ConcurrentHashMap<String, Boolean> receivedMessages;

  HobbitsSubProtocol(
      EventBus eventBus,
      String userAgent,
      TimeSeriesRecord chainData,
      ConcurrentHashMap<String, Boolean> receivedMessages) {
    this.eventBus = eventBus;
    this.userAgent = userAgent;
    this.chainData = chainData;
    this.receivedMessages = receivedMessages;
  }

  @Override
  public SubProtocolIdentifier id() {
    return BEACON_ID;
  }

  @Override
  public boolean supports(SubProtocolIdentifier subProtocolIdentifier) {
    return BEACON_ID.name().equals(subProtocolIdentifier.name())
        && BEACON_ID.version() == subProtocolIdentifier.version();
  }

  @Override
  public int versionRange(int version) {
    return 1;
  }

  @Override
  public SubProtocolHandler createHandler(RLPxService service) {
    handler =
        new HobbitsSubProtocolHandler(service, eventBus, userAgent, chainData, receivedMessages);
    return handler;
  }

  public HobbitsSubProtocolHandler handler() {
    return handler;
  }
}
