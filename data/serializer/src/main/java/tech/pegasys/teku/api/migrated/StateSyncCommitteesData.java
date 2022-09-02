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

package tech.pegasys.teku.api.migrated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateSyncCommitteesData {
  private final List<UInt64> validators;
  private final List<List<UInt64>> validatorAggregates;

  public StateSyncCommitteesData(
      final List<UInt64> validators, final List<List<UInt64>> validatorAggregates) {
    this.validators = validators;
    this.validatorAggregates = validatorAggregates;
  }

  public List<UInt64> getValidators() {
    return Collections.unmodifiableList(validators);
  }

  public List<List<UInt64>> getValidatorAggregates() {
    final List<List<UInt64>> copy = new ArrayList<>();
    for (List<UInt64> validatorAggregate : validatorAggregates) {
      copy.add(Collections.unmodifiableList(validatorAggregate));
    }
    return Collections.unmodifiableList(copy);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StateSyncCommitteesData that = (StateSyncCommitteesData) o;
    return Objects.equals(validators, that.validators)
        && Objects.equals(validatorAggregates, that.validatorAggregates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validators, validatorAggregates);
  }
}
