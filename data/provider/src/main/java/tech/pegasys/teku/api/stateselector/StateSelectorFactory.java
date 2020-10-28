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

package tech.pegasys.teku.api.stateselector;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.config.Constants;

public class StateSelectorFactory {
  private final CombinedChainDataClient client;

  public StateSelectorFactory(final CombinedChainDataClient client) {
    this.client = client;
  }

  public StateSelector defaultStateSelector(final String selectorMethod) {
    if (selectorMethod.startsWith("0x")) {
      return forStateRoot(Bytes32.fromHexString(selectorMethod));
    }
    switch (selectorMethod) {
      case ("head"):
        return headSelector();
      case ("genesis"):
        return genesisSelector();
      case ("finalized"):
        return finalizedSelector();
      case ("justified"):
        return justifiedSelector();
      default:
        return forSlot(UInt64.valueOf(selectorMethod));
    }
  }

  public StateSelector headSelector() {
    return () -> SafeFuture.completedFuture(client.getBestState());
  }

  public StateSelector finalizedSelector() {
    return () -> SafeFuture.completedFuture(client.getFinalizedState());
  }

  public StateSelector justifiedSelector() {
    return () -> client.getJustifiedState();
  }

  public StateSelector genesisSelector() {
    return () -> client.getStateAtSlotExact(UInt64.valueOf(Constants.GENESIS_SLOT));
  }

  public StateSelector forSlot(final UInt64 slot) {
    return () -> client.getStateAtSlotExact(slot);
  }

  public StateSelector forStateRoot(final Bytes32 stateRoot) {
    return () -> client.getStateByStateRoot(stateRoot);
  }
}
