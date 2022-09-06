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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.config.Constants.GOSSIP_MAX_SIZE;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.VoluntaryExitGenerator;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class VoluntaryExitTopicHandlerTest extends AbstractTopicHandlerTest<SignedVoluntaryExit> {

  private final VoluntaryExitGenerator exitGenerator =
      new VoluntaryExitGenerator(spec, beaconChainUtil.getValidatorKeys());

  @Override
  protected Eth2TopicHandler<?> createHandler(final Bytes4 forkDigest) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        GossipTopicName.VOLUNTARY_EXIT,
        Optional.empty(),
        SignedVoluntaryExit.SSZ_SCHEMA,
        GOSSIP_MAX_SIZE);
  }

  @Override
  protected BeaconChainUtil createBeaconChainUtil() {
    return BeaconChainUtil.create(spec, 5, recentChainData);
  }

  @Test
  public void handleMessage_validExit() {
    final SignedVoluntaryExit exit = exitGenerator.withEpoch(getBestState(), 3, 3);
    when(processor.process(exit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_ignoredExit() {
    final SignedVoluntaryExit exit = exitGenerator.withEpoch(getBestState(), 3, 3);
    when(processor.process(exit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    Bytes serialized = gossipEncoding.encode(exit);
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final ValidationResult result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized)).join();
    assertThat(result).isEqualTo(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final Eth2TopicHandler<SignedVoluntaryExit> topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            GossipTopicName.VOLUNTARY_EXIT,
            Optional.empty(),
            SignedVoluntaryExit.SSZ_SCHEMA,
            GOSSIP_MAX_SIZE);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/voluntary_exit/ssz_snappy");
  }

  private BeaconState getBestState() {
    return safeJoin(recentChainData.getBestState().orElseThrow());
  }
}
