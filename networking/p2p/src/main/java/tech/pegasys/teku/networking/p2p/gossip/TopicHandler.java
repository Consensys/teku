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

package tech.pegasys.teku.networking.p2p.gossip;

import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface TopicHandler {

  /**
   * Preprocess 'raw' Gossip message returning the instance which may calculate Gossip 'message-id'
   * and cache intermediate data for later message handling with {@link
   * #handleMessage(PreparedGossipMessage)}
   */
  PreparedGossipMessage prepareMessage(Bytes payload);

  /**
   * Validates and handles gossip message preprocessed earlier by {@link #prepareMessage(Bytes)}
   *
   * @param message The preprocessed gossip message
   * @return Message validation promise
   */
  SafeFuture<ValidationResult> handleMessage(PreparedGossipMessage message);

  /**
   * Expected maximum message size
   *
   * @return max message size in bytes
   */
  int getMaxMessageSize();
}
