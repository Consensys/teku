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

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import static java.lang.StrictMath.toIntExact;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult;

public class AttestationTopicHandler extends Eth2TopicHandler<Attestation> {

  private final String attestationsTopic;
  private final UnsignedLong subnetId;
  private final AttestationValidator attestationValidator;

  public AttestationTopicHandler(
      final EventBus eventBus,
      final AttestationValidator attestationValidator,
      final UnsignedLong subnetId) {
    super(eventBus);
    this.attestationValidator = attestationValidator;
    this.attestationsTopic = getTopic(subnetId);
    this.subnetId = subnetId;
  }

  private static String getTopic(final UnsignedLong subnetId) {
    return "/eth2/committee_index" + toIntExact(subnetId.longValue()) + "_beacon_attestation/ssz";
  }

  @Override
  public String getTopic() {
    return attestationsTopic;
  }

  @Override
  protected Attestation deserialize(final Bytes bytes) throws SSZException {
    return SimpleOffsetSerializer.deserialize(bytes, Attestation.class);
  }

  @Override
  protected ValidationResult validateData(final Attestation attestation) {
    return attestationValidator.validate(attestation, subnetId);
  }
}
