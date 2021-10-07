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

package tech.pegasys.teku.statetransition.block;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public interface CurrentSlotProvider {

  CurrentSlotProvider NOOP = () -> UInt64.ZERO;

  static CurrentSlotProvider create(TimeProvider timeProvider, UInt64 genesisTime, Spec spec) {
    return new CurrentSlotProviderImpl(timeProvider, genesisTime, spec);
  }

  UInt64 getCurrentSlot();

  class CurrentSlotProviderImpl implements CurrentSlotProvider {

    private final TimeProvider timeProvider;
    private final UInt64 genesisTime;
    private final Spec spec;

    public CurrentSlotProviderImpl(TimeProvider timeProvider, UInt64 genesisTime, Spec spec) {
      this.timeProvider = timeProvider;
      this.genesisTime = genesisTime;
      this.spec = spec;
    }

    @Override
    public UInt64 getCurrentSlot() {
      return spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
    }
  }
}
