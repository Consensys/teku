/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class HotValidatorIndexCacheTest {
  final IndexCache delegate = mock(IndexCache.class);
  private final HotValidatorIndexCache cache = new HotValidatorIndexCache(delegate);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();

  @Test
  public void nonFinalCacheClearsOnNewFinalizedState() {
    cache.invalidateWithNewValue(publicKey, 30, false);
    assertThat(cache.getSize()).isEqualTo(1);
    cache.updateLatestFinalizedIndex(dataStructureUtil.randomBeaconState());
    assertThat(cache.getSize()).isZero();
  }

  @Test
  public void notFoundReturnsEmpty() {
    assertThat(cache.find(dataStructureUtil.randomPositiveInt(), publicKey)).isEmpty();
  }

  @Test
  public void findWithElementInCache() {
    final int index = 2;
    cache.invalidateWithNewValue(publicKey, index, false);
    // find when index out of range
    assertThat(cache.find(index, publicKey)).isEmpty();
    // find when index in range
    assertThat(cache.find(index + 1, publicKey)).contains(2);
  }

  @Test
  public void canFindInCache() {
    final int expectedIndex = 30;
    cache.invalidateWithNewValue(publicKey, expectedIndex, false);
    assertThat(cache.getSize()).isEqualTo(1);
    assertThat(cache.find(expectedIndex + 1, publicKey)).contains(30);
  }

  @Test
  public void findWithExceptionReturnsEmpty() {
    final Map<BLSPublicKey, Integer> theMap = Mockito.<Map<BLSPublicKey, Integer>>mock();
    final HotValidatorIndexCache cache = new HotValidatorIndexCache(theMap, delegate);

    when(theMap.get(publicKey)).thenThrow(new RuntimeException());
    assertThat(cache.find(dataStructureUtil.randomPositiveInt(), publicKey)).isEmpty();
  }

  @Test
  public void getLastCachedIndex_callsThrough() {
    cache.getLastCachedIndex();
    verify(delegate).getLastCachedIndex();
  }

  @Test
  public void getFinalizedSlot_callsThrough() {
    cache.getFinalizedSlot();
    verify(delegate).getFinalizedSlot();
  }

  @Test
  public void getLatestFinalizedIndex_callsThrough() {
    cache.getLatestFinalizedIndex();
    verify(delegate).getLatestFinalizedIndex();
  }
}
