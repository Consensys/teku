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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import io.libp2p.core.pubsub.ValidationResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttesterSlashingValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class AttesterSlashingTopicHandler implements Eth2TopicHandler<AttesterSlashing> {
  private static final Logger LOG = LogManager.getLogger();
  public static String TOPIC_NAME = "attester_slashing";

  private final AttesterSlashingValidator validator;
  private final GossipEncoding gossipEncoding;
  private final Bytes4 forkDigest;
  private final GossipedOperationConsumer<AttesterSlashing> consumer;

  public AttesterSlashingTopicHandler(
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final AttesterSlashingValidator validator,
      final GossipedOperationConsumer<AttesterSlashing> consumer) {
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkInfo.getForkDigest();
    this.validator = validator;
    this.consumer = consumer;
  }

  @Override
  public ValidationResult handleMessage(final Bytes bytes) {
    try {
      AttesterSlashing attesterSlashing = deserialize(bytes);
      final InternalValidationResult internalValidationResult = validateData(attesterSlashing);
      switch (internalValidationResult) {
        case REJECT:
        case IGNORE:
          LOG.trace("Received invalid message for topic: {}", this::getTopic);
          break;
        case ACCEPT:
          consumer.forward(attesterSlashing);
          break;
        default:
          throw new UnsupportedOperationException(
              "Unexpected validation result: " + internalValidationResult);
      }
      return internalValidationResult.getGossipSubValidationResult();
    } catch (DecodingException e) {
      LOG.trace("Received malformed gossip message on {}", getTopic());
      return ValidationResult.Invalid;
    } catch (Throwable e) {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), e);
      return ValidationResult.Invalid;
    }
  }

  @Override
  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
  }

  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }

  @Override
  public Class<AttesterSlashing> getValueType() {
    return AttesterSlashing.class;
  }

  @Override
  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  protected InternalValidationResult validateData(final AttesterSlashing attesterSlashing) {
    return validator.validate(attesterSlashing);
  }
}
