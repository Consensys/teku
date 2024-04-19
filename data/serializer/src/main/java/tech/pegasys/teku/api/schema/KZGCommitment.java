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

package tech.pegasys.teku.api.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

public class KZGCommitment {
  /** The number of bytes in this value - i.e. 48 */
  private static final int SIZE = 48;

  private final Bytes bytes;

  public KZGCommitment(final Bytes bytes) {
    checkArgument(
        bytes.size() == SIZE,
        "Bytes%s should be %s bytes, but was %s bytes.",
        SIZE,
        SIZE,
        bytes.size());
    this.bytes = bytes;
  }

  public KZGCommitment(
      final tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGCommitment
          kzgCommitment) {
    this(kzgCommitment.sszSerialize());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final KZGCommitment other = (KZGCommitment) o;
    return bytes.equals(other.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes);
  }

  @Override
  public String toString() {
    return bytes.toString();
  }

  public String toHexString() {
    return bytes.toHexString();
  }

  public tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGCommitment
      asInternalKZGCommitment() {
    return tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGCommitment.fromSSZBytes(
        bytes);
  }
}
