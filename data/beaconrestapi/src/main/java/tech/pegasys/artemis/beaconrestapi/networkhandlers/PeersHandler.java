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

package tech.pegasys.artemis.beaconrestapi.networkhandlers;

import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.provider.JsonProvider;

public class PeersHandler implements BeaconRestApiHandler {

  private final P2PNetwork network;

  public PeersHandler(P2PNetwork network) {
    this.network = network;
  }

  @Override
  public String getPath() {
    return "/network/peers";
  }

  @Override
  public String handleRequest(RequestParams param) {
    if (network instanceof JvmLibP2PNetwork) {
      return JsonProvider.objectToJSON(((JvmLibP2PNetwork) network).getPeerIds().toArray());
    } else {
      return "p2pNetwork not set to libP2P";
    }
  }
}
