/*
 * Copyright 2020 ConsenSys AG.
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
import tech.pegasys.teku.bls.BLSConstants;

public class BLSSignature {
  /** The number of bytes in this value - i.e. 96 */
  private static final int SIZE = BLSConstants.BLS_SIGNATURE_SIZE;

  private final Bytes bytes;

  public BLSSignature(Bytes bytes) {
    checkArgument(
        bytes.size() == SIZE,
        "Bytes%s should be %s bytes, but was %s bytes.",
        SIZE,
        SIZE,
        bytes.size());
    this.bytes = bytes;
  }

  public BLSSignature(tech.pegasys.teku.bls.BLSSignature signature) {
    this(signature.toBytesCompressed());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BLSSignature blsSignature = (BLSSignature) o;
    return bytes.equals(blsSignature.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes);
  }

  @Override
  public String toString() {
    return bytes.toString();
  }

  public static BLSSignature fromHexString(String value) {
    return new BLSSignature(Bytes.fromHexString(value));
  }

  public String toHexString() {
    return bytes.toHexString();
  }

  public static BLSSignature empty() {
    return new BLSSignature(tech.pegasys.teku.bls.BLSSignature.empty());
  }

  public final Bytes getBytes() {
    return bytes;
  }

  public tech.pegasys.teku.bls.BLSSignature asInternalBLSSignature() {
    return tech.pegasys.teku.bls.BLSSignature.fromBytesCompressed(bytes);
  }
}
