/*
 * Copyright Consensys Software Inc., 2023
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
import static tech.pegasys.teku.kzg.ckzg4844.CKZG4844.G1_POINT_SIZE;
import static tech.pegasys.teku.kzg.ckzg4844.CKZG4844.G2_POINT_SIZE;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public record TrustedSetup(List<Bytes> g1Points, List<Bytes> g2Points) {

  public TrustedSetup {
    g1Points.forEach(this::validateG1Point);
    g2Points.forEach(this::validateG2Point);
  }

  private void validateG1Point(final Bytes g1Point) {
    checkArgument(
        g1Point.size() == G1_POINT_SIZE, "Expected G1 point to be %s bytes", G1_POINT_SIZE);
  }

  private void validateG2Point(final Bytes g2Point) {
    checkArgument(
        g2Point.size() == G2_POINT_SIZE, "Expected G2 point to be %s bytes", G2_POINT_SIZE);
  }
}
