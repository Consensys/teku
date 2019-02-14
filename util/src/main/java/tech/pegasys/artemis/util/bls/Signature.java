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

package tech.pegasys.artemis.util.bls;

import java.util.Objects;
import net.consensys.cava.bytes.Bytes48;

public class Signature {

  protected final Bytes48 c0;
  protected final Bytes48 c1;

  public Signature(Bytes48 c0, Bytes48 c1) {
    this.c0 = c0;
    this.c1 = c1;
  }

  @Override
  public int hashCode() {
    return Objects.hash(c0, c1);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Signature)) {
      return false;
    }

    Signature other = (Signature) obj;
    return Objects.equals(this.getC0(), other.getC0())
        && Objects.equals(this.getC1(), other.getC1());
  }

  /** @return the c0 */
  public Bytes48 getC0() {
    return c0;
  }

  /** @return the c1 */
  public Bytes48 getC1() {
    return c1;
  }
}
