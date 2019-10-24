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

package tech.pegasys.artemis.datastructures.networking.libp2p.rpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public final class GoodbyeMessage implements SimpleOffsetSerializable, SSZContainer {

  private final UnsignedLong reason;

  public static final UnsignedLong REASON_CLIENT_SHUT_DOWN = UnsignedLong.valueOf(1);
  public static final UnsignedLong REASON_IRRELEVANT_NETWORK = UnsignedLong.valueOf(2);
  public static final UnsignedLong REASON_FAULT_ERROR = UnsignedLong.valueOf(3);
  public static final UnsignedLong MIN_CUSTOM_REASON_CODE = UnsignedLong.valueOf(128);

  public GoodbyeMessage(UnsignedLong reason) {
    checkArgument(
        REASON_CLIENT_SHUT_DOWN.equals(reason)
            || REASON_FAULT_ERROR.equals(reason)
            || REASON_IRRELEVANT_NETWORK.equals(reason)
            || MIN_CUSTOM_REASON_CODE.compareTo(reason) <= 0,
        "Invalid reason code for Goodbye message");
    this.reason = reason;
  }

  @Override
  public int getSSZFieldCount() {
    return 1;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(SSZ.encodeUInt64(reason.longValue()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(reason);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof GoodbyeMessage)) {
      return false;
    }

    GoodbyeMessage other = (GoodbyeMessage) obj;
    return Objects.equals(this.getReason(), other.getReason());
  }

  public UnsignedLong getReason() {
    return reason;
  }
}
