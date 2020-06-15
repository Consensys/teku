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
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class AggregateAttestationTopicHandler implements Eth2TopicHandler<SignedAggregateAndProof> {
  private static final Logger LOG = LogManager.getLogger();
  public static String TOPIC_NAME = "beacon_aggregate_and_proof";

  private final SignedAggregateAndProofValidator validator;
  private final GossipEncoding gossipEncoding;
  private final Bytes4 forkDigest;
  private final GossipedOperationConsumer<ValidateableAttestation> gossipedAttestationConsumer;

  public AggregateAttestationTopicHandler(
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final SignedAggregateAndProofValidator validator,
      final GossipedOperationConsumer<ValidateableAttestation> gossipedAttestationConsumer) {
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkInfo.getForkDigest();
    this.validator = validator;
    this.gossipedAttestationConsumer = gossipedAttestationConsumer;
  }

  @Override
  public ValidationResult handleMessage(final Bytes bytes) {
    try {
      ValidateableAttestation attestation =
          ValidateableAttestation.fromSignedAggregate(deserialize(bytes));
      final InternalValidationResult internalValidationResult = validateData(attestation);
      switch (internalValidationResult) {
        case REJECT:
        case IGNORE:
          LOG.trace("Received invalid message for topic: {}", this::getTopic);
          break;
        case SAVE_FOR_FUTURE:
          LOG.trace("Deferring message for topic: {}", this::getTopic);
          gossipedAttestationConsumer.forward(attestation);
          break;
        case ACCEPT:
          attestation.markGossiped();
          gossipedAttestationConsumer.forward(attestation);
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
  public Class<SignedAggregateAndProof> getValueType() {
    return SignedAggregateAndProof.class;
  }

  @Override
  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  protected InternalValidationResult validateData(
      final ValidateableAttestation validateableAttestation) {
    return validator.validate(validateableAttestation);
  }
}
