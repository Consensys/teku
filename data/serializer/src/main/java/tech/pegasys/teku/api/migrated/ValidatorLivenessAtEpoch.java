/*
 * Copyright ConsenSys Software Inc., 2021
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

package tech.pegasys.teku.api.migrated;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorLivenessAtEpoch {
  private final UInt64 index;
  private final UInt64 epoch;
  private final boolean isLive;

  public ValidatorLivenessAtEpoch(final UInt64 index, final UInt64 epoch, final boolean isLive) {
    this.index = index;
    this.epoch = epoch;
    this.isLive = isLive;
  }

  public UInt64 getIndex() {
    return index;
  }

  public UInt64 getEpoch() {
    return epoch;
  }

  public boolean isLive() {
    return isLive;
  }

  public static SerializableTypeDefinition<ValidatorLivenessAtEpoch> getJsonTypeDefinition() {
    return SerializableTypeDefinition.object(ValidatorLivenessAtEpoch.class)
        .withField("index", UINT64_TYPE, ValidatorLivenessAtEpoch::getIndex)
        .withField("epoch", UINT64_TYPE, ValidatorLivenessAtEpoch::getEpoch)
        .withField("is_live", BOOLEAN_TYPE, ValidatorLivenessAtEpoch::isLive)
        .build();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorLivenessAtEpoch that = (ValidatorLivenessAtEpoch) o;
    return isLive == that.isLive
        && Objects.equals(index, that.index)
        && Objects.equals(epoch, that.epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, epoch, isLive);
  }

  @Override
  public String toString() {
    return "ValidatorLivenessAtEpoch{"
        + "index="
        + index
        + ", epoch="
        + epoch
        + ", isLive="
        + isLive
        + '}';
  }
}
