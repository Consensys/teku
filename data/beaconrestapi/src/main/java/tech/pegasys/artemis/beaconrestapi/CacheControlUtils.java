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

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;

public class CacheControlUtils {
  public static final String CACHE_NONE = "max-age=0";
  // Finalized max-age equates to 1 year
  public static final String CACHE_FINALIZED = "max-age=31556952";

  public static String getMaxAgeForSignedBlock(
      ChainDataProvider provider, SignedBeaconBlock signedBeaconBlock) {
    return provider.isFinalized(signedBeaconBlock) ? CACHE_FINALIZED : CACHE_NONE;
  }

  public static String getMaxAgeForSlot(ChainDataProvider provider, UnsignedLong slot) {
    return provider.isFinalized(slot) ? CACHE_FINALIZED : CACHE_NONE;
  }
}
