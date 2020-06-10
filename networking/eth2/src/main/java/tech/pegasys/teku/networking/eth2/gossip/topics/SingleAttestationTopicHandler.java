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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class SingleAttestationTopicHandler implements Eth2TopicHandler<Attestation> {
  private static final Logger LOG = LogManager.getLogger();

  private final int subnetId;
  private final AttestationValidator validator;
  private final GossipEncoding gossipEncoding;
  private final GossipedAttestationConsumer gossipedAttestationConsumer;
  private final Bytes4 forkDigest;

  public SingleAttestationTopicHandler(
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final int subnetId,
      final AttestationValidator validator,
      final GossipedAttestationConsumer gossipedAttestationConsumer) {
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkInfo.getForkDigest();
    this.validator = validator;
    this.gossipedAttestationConsumer = gossipedAttestationConsumer;
    this.subnetId = subnetId;
  }

  @Override
  public boolean handleMessage(final Bytes bytes) {
    try {
      ValidateableAttestation attestation = ValidateableAttestation.fromSingle(deserialize(bytes));
      final ValidationResult validationResult = validateData(attestation);
      switch (validationResult) {
        case INVALID:
          LOG.trace("Received invalid message for topic: {}", this::getTopic);
          return false;
        case SAVED_FOR_FUTURE:
          LOG.trace("Deferring message for topic: {}", this::getTopic);
          gossipedAttestationConsumer.accept(attestation);
          return false;
        case VALID:
          attestation.markGossiped();
          gossipedAttestationConsumer.accept(attestation);
          return true;
        default:
          throw new UnsupportedOperationException(
              "Unexpected validation result: " + validationResult);
      }
    } catch (DecodingException e) {
      LOG.trace("Received malformed gossip message on {}", getTopic());
      return false;
    } catch (Throwable e) {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), e);
      return false;
    }
  }

  @Override
  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
  }

  @Override
  public String getTopicName() {
    return "beacon_attestation_" + subnetId;
  }

  @Override
  public Class<Attestation> getValueType() {
    return Attestation.class;
  }

  @Override
  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  private ValidationResult validateData(final ValidateableAttestation attestation) {
    return validator.validate(attestation, subnetId);
  }
}
