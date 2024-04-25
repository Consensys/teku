/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositWithIndex implements Comparable<DepositWithIndex> {

  private final Deposit deposit;
  private final UInt64 index;

  public DepositWithIndex(Deposit deposit, UInt64 index) {
    this.deposit = deposit;
    this.index = index;
  }

  public Deposit getDeposit() {
    return deposit;
  }

  public UInt64 getIndex() {
    return index;
  }

  @Override
  public int compareTo(final @NotNull DepositWithIndex o) {
    return this.getIndex().compareTo(o.getIndex());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DepositWithIndex that = (DepositWithIndex) o;
    return Objects.equals(index, that.index) && Objects.equals(deposit, that.deposit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deposit, index);
  }

  @Override
  public String toString() {
    return "DepositWithIndex{" + "index=" + index + '}';
  }
}
