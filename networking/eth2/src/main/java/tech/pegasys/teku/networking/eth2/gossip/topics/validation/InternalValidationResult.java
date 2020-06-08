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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

public enum InternalValidationResult {
  ACCEPT,
  SAVE_FOR_FUTURE,
  IGNORE,
  REJECT;

  public io.libp2p.core.pubsub.ValidationResult getGossipSubValidationResult() {
    switch (this) {
      case SAVE_FOR_FUTURE:
      case IGNORE:
        return io.libp2p.core.pubsub.ValidationResult.Ignore;
      case REJECT:
        return io.libp2p.core.pubsub.ValidationResult.Invalid;
      case ACCEPT:
        return io.libp2p.core.pubsub.ValidationResult.Valid;
      default:
        throw new UnsupportedOperationException("Enum missing value");
    }
  }
}
