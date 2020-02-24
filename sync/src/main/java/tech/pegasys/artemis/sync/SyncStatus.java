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

package tech.pegasys.artemis.sync;

import com.google.common.primitives.UnsignedLong;

public class SyncStatus {

  public UnsignedLong starting_slot;
  public UnsignedLong current_slot;
  public UnsignedLong highest_slot;

  public SyncStatus(
      final UnsignedLong starting_slot,
      final UnsignedLong current_slot,
      final UnsignedLong highest_slot) {
    this.starting_slot = starting_slot;
    this.current_slot = current_slot;
    this.highest_slot = highest_slot;
  }
}
