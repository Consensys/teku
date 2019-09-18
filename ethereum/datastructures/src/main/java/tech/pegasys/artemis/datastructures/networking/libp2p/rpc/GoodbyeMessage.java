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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public final class GoodbyeMessage implements SimpleOffsetSerializable, SSZContainer {

  private final UnsignedLong reason;

  public static final int REASON_CLIENT_SHUT_DOWN = 1;
  public static final int REASON_IRRELEVANT_NETWORK = 2;
  public static final int REASON_FAULT_ERROR = 3;
  public static final int MIN_CUSTOM_REASON_CODE = 128; //

  public GoodbyeMessage(UnsignedLong reason) throws Exception {
    if (UnsignedLong.valueOf(REASON_CLIENT_SHUT_DOWN).compareTo(reason) != 0
        && UnsignedLong.valueOf(REASON_FAULT_ERROR).compareTo(reason) != 0
        && UnsignedLong.valueOf(REASON_IRRELEVANT_NETWORK).compareTo(reason) != 0
        && UnsignedLong.valueOf(MIN_CUSTOM_REASON_CODE).compareTo(reason) == 1) {
      throw new Exception(
          "Invalid reason code for Goodbye message");
    }
    this.reason = reason;
  }

  @Override
  public int getSSZFieldCount() { return 1; }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>(List.of(SSZ.encodeUInt64(reason.longValue())));
    return fixedPartsList;
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
    return Objects.equals(this.reason(), other.reason());
  }

  public UnsignedLong reason() { return reason; }
}
