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

package tech.pegasys.teku.storage.api;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StubReorgEventChannel implements ReorgEventChannel {

  @Override
  public void reorgOccurred(
      final Bytes32 bestBlockRoot,
      final UInt64 bestSlot,
      final Bytes32 bestStateRoot,
      final Bytes32 oldBestBlockRoot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot) {}
}
