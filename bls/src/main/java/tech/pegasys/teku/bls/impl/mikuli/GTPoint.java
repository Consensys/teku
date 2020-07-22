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

package tech.pegasys.teku.bls.impl.mikuli;

import java.util.Objects;
import org.apache.milagro.amcl.BLS381.FP12;

/**
 * GT is the object that holds the result of the pairing operation. Points in GT are elements of
 * Fq12.
 */
public final class GTPoint {

  private final FP12 point;

  public GTPoint(FP12 point) {
    this.point = point;
  }

  public GTPoint mul(GTPoint other) {
    FP12 newPoint = new FP12(other.point);
    newPoint.mul(point);
    return new GTPoint(newPoint);
  }

  public boolean isunity() {
    return point.isunity();
  }

  public FP12 getPoint() {
    return point;
  }

  @Override
  public int hashCode() {
    return Objects.hash(point.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof GTPoint)) {
      return false;
    }
    GTPoint other = (GTPoint) obj;
    return point.equals(other.point);
  }
}
