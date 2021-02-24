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

package tech.pegasys.teku.spec.statetransition.epoch.status;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class InclusionInfo {

  private final UInt64 delay;
  private final int proposerIndex;

  public InclusionInfo(final UInt64 delay, final UInt64 proposerIndex) {
    this.delay = delay;
    this.proposerIndex = proposerIndex.intValue();
  }

  public UInt64 getDelay() {
    return delay;
  }

  public int getProposerIndex() {
    return proposerIndex;
  }
}
