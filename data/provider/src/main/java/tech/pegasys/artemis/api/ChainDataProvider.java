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

package tech.pegasys.artemis.api;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;

public class ChainDataProvider {
  @SuppressWarnings("unused")
  private final CombinedChainDataClient combinedChainDataClient;

  private final ChainStorageClient chainStorageClient;

  public ChainDataProvider(
      final ChainStorageClient chainStorageClient,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.chainStorageClient = chainStorageClient;
  }

  public Optional<UnsignedLong> getGenesisTime() {
    if (chainStorageClient == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(chainStorageClient.getGenesisTime());
  }

  public Optional<BeaconHead> getBeaconHead() {
    if (chainStorageClient == null) {
      return Optional.empty();
    }

    Bytes32 headBlockRoot = chainStorageClient.getBestBlockRoot();
    if (headBlockRoot == null) {
      return Optional.empty();
    }
    Bytes32 headStateRoot = chainStorageClient.getBestBlockRootState().hash_tree_root();
    BeaconHead result =
        new BeaconHead(chainStorageClient.getBestSlot(), headBlockRoot, headStateRoot);
    return Optional.of(result);
  }
}
