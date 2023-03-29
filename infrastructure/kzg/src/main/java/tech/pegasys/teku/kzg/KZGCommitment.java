/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;

public final class KZGCommitment {

  public static final int KZG_COMMITMENT_SIZE = 48;

  /**
   * Creates 0x00..00 for point-at-infinity
   *
   * @return point-at-infinity as per the Eth2 spec
   */
  public static KZGCommitment infinity() {
    return KZGCommitment.fromBytesCompressed(Bytes48.ZERO);
  }

  public static KZGCommitment fromHexString(final String hexString) {
    return KZGCommitment.fromBytesCompressed(Bytes48.fromHexString(hexString));
  }

  public static KZGCommitment fromSSZBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == KZG_COMMITMENT_SIZE,
        "Expected " + KZG_COMMITMENT_SIZE + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes,
        reader -> new KZGCommitment(Bytes48.wrap(reader.readFixedBytes(KZG_COMMITMENT_SIZE))));
  }

  public static KZGCommitment fromBytesCompressed(final Bytes48 bytes) {
    return new KZGCommitment(bytes);
  }

  public static KZGCommitment fromArray(final byte[] bytes) {
    return fromBytesCompressed(Bytes48.wrap(bytes));
  }

  private final Bytes48 bytesCompressed;

  public KZGCommitment(final Bytes48 bytesCompressed) {
    this.bytesCompressed = bytesCompressed;
  }

  /**
   * Returns the SSZ serialization of the <em>compressed</em> form of the commitment
   *
   * @return the serialization of the compressed form of the commitment.
   */
  public Bytes toSSZBytes() {
    return SSZ.encode(writer -> writer.writeFixedBytes(getBytesCompressed()));
  }

  public Bytes48 getBytesCompressed() {
    return bytesCompressed;
  }

  public byte[] toArrayUnsafe() {
    return bytesCompressed.toArrayUnsafe();
  }

  public String toAbbreviatedString() {
    return getBytesCompressed().toUnprefixedHexString().substring(0, 7);
  }

  public String toHexString() {
    return getBytesCompressed().toHexString();
  }

  @Override
  public String toString() {
    return getBytesCompressed().toString();
  }

  @Override
  public boolean equals(final Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof KZGCommitment)) {
      return false;
    }

    final KZGCommitment other = (KZGCommitment) obj;
    return Objects.equals(this.getBytesCompressed(), other.getBytesCompressed());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBytesCompressed());
  }
}
