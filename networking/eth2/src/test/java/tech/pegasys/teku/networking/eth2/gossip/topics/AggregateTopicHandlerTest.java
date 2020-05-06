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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult;

public class AggregateTopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final SignedAggregateAndProofValidator validator =
      mock(SignedAggregateAndProofValidator.class);
  private final AggregateTopicHandler topicHandler =
      new AggregateTopicHandler(
          gossipEncoding, dataStructureUtil.randomForkInfo(), validator, eventBus);

  @Test
  public void handleMessage_validAggregate() {
    final SignedAggregateAndProof aggregate = dataStructureUtil.randomSignedAggregateAndProof();
    when(validator.validate(aggregate)).thenReturn(ValidationResult.VALID);

    final boolean result = topicHandler.handleMessage(gossipEncoding.encode(aggregate));
    assertThat(result).isTrue();
    verify(eventBus).post(aggregate);
  }

  @Test
  public void handleMessage_savedForFuture() {
    final SignedAggregateAndProof aggregate = dataStructureUtil.randomSignedAggregateAndProof();
    when(validator.validate(aggregate)).thenReturn(ValidationResult.SAVED_FOR_FUTURE);

    final boolean result = topicHandler.handleMessage(gossipEncoding.encode(aggregate));
    assertThat(result).isFalse();
    verify(eventBus).post(aggregate);
  }

  @Test
  public void handleMessage_invalidAggregate() {
    final SignedAggregateAndProof aggregate = dataStructureUtil.randomSignedAggregateAndProof();
    when(validator.validate(aggregate)).thenReturn(ValidationResult.INVALID);

    final boolean result = topicHandler.handleMessage(gossipEncoding.encode(aggregate));
    assertThat(result).isFalse();
    verify(eventBus, never()).post(aggregate);
  }
}
