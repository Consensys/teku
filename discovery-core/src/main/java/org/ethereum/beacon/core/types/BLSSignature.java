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

package org.ethereum.beacon.core.types;

import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.WrappingBytes96;

@SSZSerializable(serializeAs = Bytes96.class)
public class BLSSignature extends WrappingBytes96 {

  public static final BLSSignature ZERO = new BLSSignature(Bytes96.ZERO);

  public static BLSSignature wrap(Bytes96 signatureBytes) {
    return new BLSSignature(signatureBytes);
  }

  public BLSSignature(Bytes96 value) {
    super(value);
  }

  @Override
  public String toString() {
    String s = super.toString();
    return s.substring(2, 6) + "..." + s.substring(190);
  }

  public String toHexString() {
    return super.toString();
  }
}
