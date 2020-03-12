/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.beaconrestapi;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_FINALIZED;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

public class CacheControlUtilsTest {

  SignedBeaconBlock signedBlock =
      new SignedBeaconBlock(DataStructureUtil.randomSignedBeaconBlock(1, 1));
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @Test
  void getMaxAgeForSignedBlock_shouldSetCacheNoneIfNotFinalized() {
    when(provider.isFinalized(signedBlock)).thenReturn(false);
    String cacheControl = CacheControlUtils.getMaxAgeForSignedBlock(provider, signedBlock);
    assertThat(cacheControl).isEqualTo(CACHE_NONE);
  }

  @Test
  void getMaxAgeForSignedBlock_shouldSetIfFinalized() {
    when(provider.isFinalized(signedBlock)).thenReturn(true);
    String cacheControl = CacheControlUtils.getMaxAgeForSignedBlock(provider, signedBlock);
    assertThat(cacheControl).isEqualTo(CACHE_FINALIZED);
  }

  @Test
  void getMaxAgeForSlot_shouldSetCacheNoneIfNotFinalized() {
    when(provider.isFinalized(ZERO)).thenReturn(false);
    String cacheControl = CacheControlUtils.getMaxAgeForSlot(provider, ZERO);
    assertThat(cacheControl).isEqualTo(CACHE_NONE);
  }

  @Test
  void getMaxAgeForSlot_shouldSetIfFinalized() {
    when(provider.isFinalized(ZERO)).thenReturn(true);
    String cacheControl = CacheControlUtils.getMaxAgeForSlot(provider, ZERO);
    assertThat(cacheControl).isEqualTo(CACHE_FINALIZED);
  }
}
