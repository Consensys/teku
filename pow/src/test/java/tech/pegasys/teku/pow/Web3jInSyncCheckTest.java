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

package tech.pegasys.teku.pow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthSyncing;

class Web3jInSyncCheckTest {
  @Test
  void shouldNotBeSyncingWhenSyncingIsFalse() {
    final EthSyncing response = new EthSyncing();
    final EthSyncing.Result result = new EthSyncing.Result();
    result.setSyncing(false);
    response.setResult(result);
    assertThat(Web3jInSyncCheck.isSyncing("nodeId", response)).isFalse();
  }

  @Test
  void shouldNotBeSyncingWhenCurrentBlockCloseToHighestBlock() {
    final EthSyncing response = new EthSyncing();
    final EthSyncing.Syncing result = new EthSyncing.Syncing("0x0", "0x10", "0x19", null, null);
    result.setSyncing(true);
    response.setResult(result);
    assertThat(Web3jInSyncCheck.isSyncing("nodeId", response)).isFalse();
  }

  @Test
  void shouldBeSyncingWhenCurrentBlockNotCloseToHighestBlock() {
    final EthSyncing response = new EthSyncing();
    final EthSyncing.Syncing result = new EthSyncing.Syncing("0x0", "0x10", "0x30", null, null);
    result.setSyncing(true);
    response.setResult(result);
    assertThat(Web3jInSyncCheck.isSyncing("nodeId", response)).isTrue();
  }

  @Test
  void shouldBeSyncingWhenResultIsNotSyncingType() {
    final EthSyncing response = new EthSyncing();
    final EthSyncing.Result result = new EthSyncing.Result();
    result.setSyncing(true);
    response.setResult(result);
    assertThat(Web3jInSyncCheck.isSyncing("nodeId", response)).isTrue();
  }
}
