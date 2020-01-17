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

package tech.pegasys.artemis.pow.event;

import com.google.common.primitives.UnsignedLong;

public class Eth1DataRequest {

  private final UnsignedLong block_timestamp_lower_limit;
  private final UnsignedLong block_timestamp_upper_limit;

  public Eth1DataRequest(
      UnsignedLong block_timestamp_lower_limit, UnsignedLong block_timestamp_upper_limit) {
    this.block_timestamp_lower_limit = block_timestamp_lower_limit;
    this.block_timestamp_upper_limit = block_timestamp_upper_limit;
  }

  public UnsignedLong getBlock_timestamp_lower_limit() {
    return block_timestamp_lower_limit;
  }

  public UnsignedLong getBlock_timestamp_upper_limit() {
    return block_timestamp_upper_limit;
  }
}
