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

import net.consensys.cava.rlpx.RLPxService;
import net.consensys.cava.rlpx.wire.SubProtocol;
import net.consensys.cava.rlpx.wire.SubProtocolHandler;
import net.consensys.cava.rlpx.wire.SubProtocolIdentifier;

final class BeaconSubprotocol implements SubProtocol {

  static final SubProtocolIdentifier BEACON_ID = SubProtocolIdentifier.of("bea", 1);

  @Override
  public SubProtocolIdentifier id() {
    return BEACON_ID;
  }

  @Override
  public boolean supports(SubProtocolIdentifier subProtocolIdentifier) {
    return BEACON_ID.name().equals(subProtocolIdentifier.name());
  }

  @Override
  public int versionRange(int version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SubProtocolHandler createHandler(RLPxService service) {
    throw new UnsupportedOperationException();
  }
}
