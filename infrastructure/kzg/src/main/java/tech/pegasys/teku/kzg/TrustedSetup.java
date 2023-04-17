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

package tech.pegasys.teku.kzg;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

public class TrustedSetup {
  private final List<Bytes48> g1Points;
  private final List<Bytes> g2Points;

  private void validateG2Point(Bytes g2Point) {
    checkArgument(g2Point.size() == 96, "Expected G2 point to be 96 bytes");
  }

  public TrustedSetup(final List<Bytes48> g1Points, final List<Bytes> g2Points) {
    g2Points.forEach(this::validateG2Point);
    this.g1Points = g1Points;
    this.g2Points = g2Points;
  }

  public List<Bytes48> getG1Points() {
    return g1Points;
  }

  public List<Bytes> getG2Points() {
    return g2Points;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TrustedSetup that = (TrustedSetup) o;
    return Objects.equals(g1Points, that.g1Points) && Objects.equals(g2Points, that.g2Points);
  }

  @Override
  public int hashCode() {
    return Objects.hash(g1Points, g2Points);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("g1Points", g1Points)
        .add("g2Points", g2Points)
        .toString();
  }
}
