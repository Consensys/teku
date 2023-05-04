/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.execution.versions.capella;

import java.util.Objects;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.GetPayloadResponseBellatrix;

public class GetPayloadResponseCapella extends GetPayloadResponseBellatrix {

  private final UInt256 blockValue;

  public GetPayloadResponseCapella(
      final ExecutionPayload executionPayload, final UInt256 blockValue) {
    super(executionPayload);
    this.blockValue = blockValue;
  }

  @Override
  public UInt256 getBlockValue() {
    return blockValue;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GetPayloadResponseCapella)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final GetPayloadResponseCapella that = (GetPayloadResponseCapella) o;
    return Objects.equals(blockValue, that.blockValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), blockValue);
  }
}
