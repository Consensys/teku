/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.time.Duration;
import tech.pegasys.teku.networking.p2p.rpc.StreamTimeoutException;

/**
 * This class holds constants related to handling rpc request timeouts. See:
 * https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/p2p-interface.md#configuration
 */
public abstract class RpcTimeouts {

  // The maximum time to wait for first byte of request response (time-to-first-byte).
  static final Duration TTFB_TIMEOUT = Duration.ofSeconds(5);
  // The maximum time for complete response transfer.
  public static final Duration RESP_TIMEOUT = Duration.ofSeconds(10);

  public static class RpcTimeoutException extends StreamTimeoutException {

    public RpcTimeoutException(final String message, final Duration timeout) {
      super(generateMessage(message, timeout));
    }

    private static String generateMessage(final String message, final Duration timeout) {
      return String.format("Rpc request timed out after %d sec: %s", timeout.getSeconds(), message);
    }
  }
}
