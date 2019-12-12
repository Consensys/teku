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

package tech.pegasys.artemis.test.acceptance.dsl.data;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class FinalizedCheckpoint {

  private final UnsignedLong epoch;
  private final Bytes32 root;

  public FinalizedCheckpoint(final UnsignedLong epoch, final Bytes32 root) {
    this.epoch = epoch;
    this.root = root;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FinalizedCheckpoint that = (FinalizedCheckpoint) o;
    return Objects.equals(epoch, that.epoch) && Objects.equals(root, that.root);
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, root);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("epoch", epoch).add("root", root).toString();
  }

  public UnsignedLong getEpoch() {
    return epoch;
  }

  public Bytes32 getRoot() {
    return root;
  }
}
