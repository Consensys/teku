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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

class ValidatorIndexCacheTrackerTest {

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final Spec spec = TestSpecFactory.createDefault();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final AnchorPoint anchorPoint = dataStructureUtil.randomAnchorPoint(UInt64.ZERO);

  private final ValidatorIndexCacheTracker validatorIndexCacheTracker =
      new ValidatorIndexCacheTracker(recentChainData);

  @BeforeEach
  public void setup() {
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getLatestFinalized()).thenReturn(anchorPoint);
  }

  @Test
  public void updatesCacheLatestFinalizedIndex() {
    assertThat(getCacheLatestFinalizedIndex(anchorPoint.getState())).isEqualTo(-1);

    validatorIndexCacheTracker.onNewFinalizedCheckpoint(mock(Checkpoint.class), false);

    assertThat(getCacheLatestFinalizedIndex(anchorPoint.getState()))
        .isNotEqualTo(-1)
        .isEqualTo(anchorPoint.getState().getValidators().size() - 1);
  }

  private int getCacheLatestFinalizedIndex(final BeaconState state) {
    return BeaconStateCache.getTransitionCaches(state)
        .getValidatorIndexCache()
        .getLatestFinalizedIndex();
  }
}
