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

package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.web3j.protocol.core.methods.response.Log;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class DepositWithIndex extends Deposit implements Comparable<DepositWithIndex> {

  private final UnsignedLong index;
  private final Log log;

  public DepositWithIndex(SSZVector<Bytes32> proof, DepositData data, UnsignedLong index) {
    super(proof, data);
    this.index = index;
    log = null;
  }

  public DepositWithIndex(DepositData data, UnsignedLong index) {
    super(data);
    this.index = index;
    log = null;
  }

  public DepositWithIndex(DepositData data, UnsignedLong index, Log log) {
    super(data);
    this.index = index;
    this.log = log;
  }

  public UnsignedLong getIndex() {
    return index;
  }

  public Log getLog() {
    return log;
  }

  @Override
  public int compareTo(@NotNull DepositWithIndex o) {
    return this.getIndex().compareTo(o.getIndex());
  }
}
