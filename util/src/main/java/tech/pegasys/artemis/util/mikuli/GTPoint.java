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

package tech.pegasys.artemis.util.mikuli;

import java.util.Objects;
import org.apache.milagro.amcl.BLS381.FP12;

/**
 * GT is the object that holds the result of the pairing operation. Points in GT are elements of
 * Fq12.
 */
final class GTPoint {

  private final FP12 point;

  GTPoint(FP12 point) {
    this.point = point;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((point == null) ? 0 : point.hashCode());
    return result;
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
