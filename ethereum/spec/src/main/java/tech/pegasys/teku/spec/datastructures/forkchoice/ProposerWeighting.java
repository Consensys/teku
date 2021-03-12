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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerWeighting {
  private final Bytes32 targetRoot;
  private final UInt64 weight;

  public ProposerWeighting(final Bytes32 targetRoot, final UInt64 weight) {
    this.targetRoot = targetRoot;
    this.weight = weight;
  }

  public Bytes32 getTargetRoot() {
    return targetRoot;
  }

  public UInt64 getWeight() {
    return weight;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerWeighting that = (ProposerWeighting) o;
    return Objects.equals(targetRoot, that.targetRoot) && Objects.equals(weight, that.weight);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetRoot, weight);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("targetRoot", targetRoot)
        .add("weight", weight)
        .toString();
  }
}
