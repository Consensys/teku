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

package tech.pegasys.teku.bls.mikuli;

import java.util.Objects;
import org.apache.milagro.amcl.BLS381.BIG;

/** This class represents an ordinary scalar value. */
final class Scalar {

  private final BIG value;

  Scalar(BIG value) {
    this.value = value;
  }

  BIG value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Scalar scalar = (Scalar) o;
    return Objects.equals(value.toString(), scalar.value.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
