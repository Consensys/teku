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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding.ForkDigestToMilestone;
import tech.pegasys.teku.networking.eth2.gossip.encoding.SnappyPreparedGossipMessage.Uncompressor;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SnappyPreparedGossipMessageTest {

  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.ONE);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BeaconStateSchema<?, ?> schema =
      spec.forMilestone(SpecMilestone.PHASE0).getSchemaDefinitions().getBeaconStateSchema();
  private final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();

  final Bytes4 phase0ForkDigest = Bytes4.fromHexStringLenient("0x01");
  final Bytes4 altairForkDigest = Bytes4.fromHexStringLenient("0x02");
  final Bytes4 otherForkDigest = Bytes4.fromHexStringLenient("0x03");

  final ForkDigestToMilestone forkDigestToMilestone =
      ForkDigestToMilestone.fromMap(
          Map.of(phase0ForkDigest, SpecMilestone.PHASE0, altairForkDigest, SpecMilestone.ALTAIR));

  final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  final Uncompressor validUncompressor = (bytes, __) -> bytes;
  final Uncompressor invalidUncompressor =
      (bytes, __) -> {
        throw new DecodingException("testing");
      };

  @Test
  public void create_forUnknownForkDigest() {
    final String topic = GossipTopics.getTopic(otherForkDigest, "test", gossipEncoding);
    assertThatThrownBy(() -> getAltairMessage(messageBytes, topic, validUncompressor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Failed to associate a milestone with the forkDigest in topic: " + topic);
  }

  @Test
  public void getMessageId_phase0Valid() {
    final String topic = GossipTopics.getTopic(phase0ForkDigest, "test", gossipEncoding);
    final SnappyPreparedGossipMessage message =
        getPhase0Message(messageBytes, topic, validUncompressor);

    final Bytes actual = message.getMessageId();
    final MessageIdCalculator expectedMessageIdCalculator =
        new MesssageIdCalculatorPhase0(messageBytes);
    final Bytes expected = expectedMessageIdCalculator.getValidMessageId(messageBytes);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.size()).isEqualTo(20);
  }

  @Test
  public void getMessageId_phase0Invalid() {
    final String topic = GossipTopics.getTopic(phase0ForkDigest, "test", gossipEncoding);
    final SnappyPreparedGossipMessage message =
        getPhase0Message(messageBytes, topic, invalidUncompressor);

    final Bytes actual = message.getMessageId();
    final MessageIdCalculator expectedMessageIdCalculator =
        new MesssageIdCalculatorPhase0(messageBytes);
    final Bytes expected = expectedMessageIdCalculator.getInvalidMessageId();
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.size()).isEqualTo(20);
  }

  @Test
  public void getMessageId_altairValid() {
    final String topic = GossipTopics.getTopic(altairForkDigest, "test", gossipEncoding);
    final SnappyPreparedGossipMessage message =
        getAltairMessage(messageBytes, topic, validUncompressor);

    final Bytes actual = message.getMessageId();
    final MessageIdCalculator expectedMessageIdCalculator =
        new MesssageIdCalculatorAltair(messageBytes, topic);
    final Bytes expected = expectedMessageIdCalculator.getValidMessageId(messageBytes);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.size()).isEqualTo(20);
  }

  @Test
  public void getMessageId_altairInvalid() {
    final String topic = GossipTopics.getTopic(altairForkDigest, "test", gossipEncoding);
    final SnappyPreparedGossipMessage message =
        getAltairMessage(messageBytes, topic, invalidUncompressor);

    final Bytes actual = message.getMessageId();
    final MessageIdCalculator expectedMessageIdCalculator =
        new MesssageIdCalculatorAltair(messageBytes, topic);
    final Bytes expected = expectedMessageIdCalculator.getInvalidMessageId();
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.size()).isEqualTo(20);
  }

  @Test
  public void getMessageId_altair_createUniqueIdsWhenTopicsDiffer() {
    final String topic1 = GossipTopics.getTopic(altairForkDigest, "test1", gossipEncoding);
    final String topic2 = GossipTopics.getTopic(altairForkDigest, "test2", gossipEncoding);
    final SnappyPreparedGossipMessage message1 =
        getAltairMessage(messageBytes, topic1, validUncompressor);
    final SnappyPreparedGossipMessage message2 =
        getAltairMessage(messageBytes, topic2, validUncompressor);

    final Bytes result1 = message1.getMessageId();
    final Bytes result2 = message2.getMessageId();
    assertThat(result1).isNotEqualTo(result2);
  }

  @Test
  public void getMessageId_generateUniqueIdsBasedOnValidityAndMilestone() {
    final String altairTopic = GossipTopics.getTopic(altairForkDigest, "test", gossipEncoding);
    final String phase0Topic = GossipTopics.getTopic(phase0ForkDigest, "test", gossipEncoding);

    final List<SnappyPreparedGossipMessage> preparedMessages =
        List.of(
            getAltairMessage(messageBytes, altairTopic, validUncompressor),
            getAltairMessage(messageBytes, altairTopic, invalidUncompressor),
            getAltairMessage(messageBytes, phase0Topic, validUncompressor),
            getAltairMessage(messageBytes, phase0Topic, invalidUncompressor));

    final HashSet<Bytes> messageIds = new HashSet<>();
    for (SnappyPreparedGossipMessage preparedMessage : preparedMessages) {
      messageIds.add(preparedMessage.getMessageId());
    }

    assertThat(messageIds).hasSize(preparedMessages.size());
  }

  private SnappyPreparedGossipMessage getPhase0Message(
      final Bytes rawMessage, final String topic, final Uncompressor uncompressor) {
    return SnappyPreparedGossipMessage.create(
        topic, rawMessage, forkDigestToMilestone, schema, uncompressor);
  }

  private SnappyPreparedGossipMessage getAltairMessage(
      final Bytes rawMessage, final String topic, final Uncompressor uncompressor) {
    return SnappyPreparedGossipMessage.create(
        topic, rawMessage, forkDigestToMilestone, schema, uncompressor);
  }
}
