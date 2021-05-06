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

import java.math.BigInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthSyncing;
import org.web3j.protocol.core.methods.response.EthSyncing.Syncing;

public class Web3jInSyncCheck {
  private static final Logger LOG = LogManager.getLogger();
  private static final BigInteger TOLERANCE = BigInteger.TEN;

  public static boolean isSyncing(final String id, final EthSyncing syncingResponse) {
    if (!syncingResponse.isSyncing()) {
      return false;
    }
    if (!(syncingResponse.getResult() instanceof EthSyncing.Syncing)) {
      return syncingResponse.isSyncing();
    }
    try {
      final Syncing syncingDetails = (Syncing) syncingResponse.getResult();
      final BigInteger currentBlock = parseJsonRpcValue(syncingDetails.getCurrentBlock());
      final BigInteger highestBlock = parseJsonRpcValue(syncingDetails.getHighestBlock());
      if (currentBlock.add(TOLERANCE).compareTo(highestBlock) >= 0) {
        // If we're close to the chain head, consider the node in sync
        // Avoids marking the node as invalid while it imports the latest block
        LOG.debug(
            "Eth1 endpiont {} syncing but close to head so considering it valid. Current block {} Highest block: {}",
            id,
            currentBlock,
            highestBlock);
        return false;
      }
      return true;
    } catch (final NumberFormatException | NullPointerException e) {
      LOG.error("Failed to parse syncing details from eth1 endpoint {}", id, e);
      return syncingResponse.isSyncing();
    }
  }

  private static BigInteger parseJsonRpcValue(final String value) {
    return value.startsWith("0x")
        ? new BigInteger(value.substring("0x".length()), 16)
        : new BigInteger(value);
  }
}
