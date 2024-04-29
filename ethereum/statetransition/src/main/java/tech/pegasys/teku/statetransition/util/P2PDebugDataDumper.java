/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.util;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public interface P2PDebugDataDumper {

  P2PDebugDataDumper NOOP =
      new P2PDebugDataDumper() {
        @Override
        public void saveGossipMessageDecodingError(
            final String topic,
            final Optional<UInt64> arrivalTimestamp,
            final Supplier<Bytes> originalMessage,
            final Throwable error) {}

        @Override
        public void saveGossipRejectedMessageToFile(
            final String topic,
            final Optional<UInt64> arrivalTimestamp,
            final Supplier<Bytes> decodedMessage,
            final Optional<String> reason) {}

        @Override
        public void saveInvalidBlockToFile(
            final SignedBeaconBlock block,
            final String failureReason,
            final Optional<Throwable> failureCause) {}
      };

  void saveGossipMessageDecodingError(
      String topic,
      Optional<UInt64> arrivalTimestamp,
      Supplier<Bytes> originalMessage,
      Throwable error);

  void saveGossipRejectedMessageToFile(
      String topic,
      Optional<UInt64> arrivalTimestamp,
      Supplier<Bytes> decodedMessage,
      Optional<String> reason);

  void saveInvalidBlockToFile(
      SignedBeaconBlock block, String failureReason, Optional<Throwable> failureCause);
}
