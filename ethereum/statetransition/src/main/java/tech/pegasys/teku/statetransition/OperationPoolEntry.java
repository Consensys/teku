/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition;

import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.MessageWithValidatorId;

public class OperationPoolEntry<T extends MessageWithValidatorId>
    implements Comparable<OperationPoolEntry<T>> {

  private final T message;
  private final boolean isLocal;

  private UInt64 timeSubmitted;

  public OperationPoolEntry(T message, boolean isLocal, final UInt64 timeSubmitted) {
    this.message = message;
    this.isLocal = isLocal;
    this.timeSubmitted = timeSubmitted;
  }

  public T getMessage() {
    return message;
  }

  public boolean isLocal() {
    return isLocal;
  }

  public UInt64 getTimeSubmitted() {
    return timeSubmitted;
  }

  public void setTimeSubmitted(final UInt64 timeSubmitted) {
    this.timeSubmitted = timeSubmitted;
  }

  @Override
  public int compareTo(@NotNull OperationPoolEntry<T> o) {
    if (isLocal && !o.isLocal) {
      return -1;
    }
    if (o.isLocal && !isLocal) {
      return 1;
    }
    return 0;
  }
}
