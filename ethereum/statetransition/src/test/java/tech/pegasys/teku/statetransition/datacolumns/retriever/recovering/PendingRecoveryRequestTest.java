/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.datacolumns.retriever.recovering;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetrieverTest.CHECK_INTERVAL;
import static tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetrieverTest.RECOVERY_TIMEOUT;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PendingRecoveryRequestTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1_000_000);

  @Test
  void shouldCreatePendingRecoveryRequestAndCancelAfterTimeout() {
    final PendingRecoveryRequest request = createPendingRecoveryRequest(Optional.empty());
    request.checkTimeout(timeProvider.getTimeInMillis().plus(RECOVERY_TIMEOUT.toMillis()));

    assertThat(request.getFuture().isDone()).isTrue();
  }

  @Test
  void futureIsNotCompleteWhenDownloadFails() {
    final PendingRecoveryRequest request = createPendingRecoveryRequest(Optional.empty());

    request.getDownloadFuture().cancel(true);

    assertThat(request.getFuture().isDone()).isFalse();
    assertThat(request.getDownloadFuture().isCancelled()).isTrue();
    assertThat(request.isFailedDownloading()).isTrue();
  }

  @Test
  void allFuturesStoppedAfterRequestCancelled() {
    final PendingRecoveryRequest request = createPendingRecoveryRequest(Optional.empty());

    request.cancel();

    assertThat(request.getFuture().isDone()).isTrue();
    assertThat(request.getDownloadFuture().isCancelled()).isTrue();
    assertThat(request.isFailedDownloading()).isTrue();
  }

  @Test
  void canGetColumnDetails() {
    final int column = 13;
    final DataColumnSlotAndIdentifier id =
        SidecarRetrieverTest.createId(dataStructureUtil.randomBeaconBlock(100), column);
    final PendingRecoveryRequest request = createPendingRecoveryRequest(Optional.of(id));
    assertThat(request.getSlot()).isEqualTo(id.getSlotAndBlockRoot().getSlot());
    assertThat(request.getBlockRoot()).isEqualTo(id.getSlotAndBlockRoot().getBlockRoot());
    assertThat(request.getSlotAndBlockRoot()).isEqualTo(id.getSlotAndBlockRoot());
    assertThat(request.getIndex().intValue()).isEqualTo(column);
  }

  final PendingRecoveryRequest createPendingRecoveryRequest(
      final Optional<DataColumnSlotAndIdentifier> maybeId) {
    final DataColumnSlotAndIdentifier id =
        maybeId.orElse(SidecarRetrieverTest.createId(dataStructureUtil.randomBeaconBlock(), 1));
    return new PendingRecoveryRequest(
        id, new SafeFuture<>(), timeProvider.getTimeInMillis(), RECOVERY_TIMEOUT, CHECK_INTERVAL);
  }
}
