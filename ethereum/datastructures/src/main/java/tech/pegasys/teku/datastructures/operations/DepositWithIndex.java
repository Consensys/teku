/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;

public class DepositWithIndex extends Deposit implements Comparable<DepositWithIndex> {

  private final UnsignedLong index;

  public DepositWithIndex(SSZVector<Bytes32> proof, DepositData data, UnsignedLong index) {
    super(proof, data);
    this.index = index;
  }

  public DepositWithIndex(DepositData data, UnsignedLong index) {
    super(data);
    this.index = index;
  }

  public UnsignedLong getIndex() {
    return index;
  }

  @Override
  public int compareTo(@NotNull DepositWithIndex o) {
    return this.getIndex().compareTo(o.getIndex());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DepositWithIndex that = (DepositWithIndex) o;
    return index.equals(that.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), index);
  }

  @Override
  public String toString() {
    return "DepositWithIndex{" + "index=" + index + '}';
  }
}
