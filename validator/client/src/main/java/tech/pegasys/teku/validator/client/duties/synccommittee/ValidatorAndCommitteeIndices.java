/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.validator.client.Validator;

class ValidatorAndCommitteeIndices {

  private final Validator validator;
  private final int validatorIndex;
  private final Set<Integer> committeeIndices = new HashSet<>();

  ValidatorAndCommitteeIndices(final Validator validator, final int validatorIndex) {
    this.validator = validator;
    this.validatorIndex = validatorIndex;
  }

  public void addCommitteeIndex(final int subcommitteeIndex) {
    committeeIndices.add(subcommitteeIndex);
  }

  public void addCommitteeIndices(final Collection<Integer> subcommitteeIndex) {
    committeeIndices.addAll(subcommitteeIndex);
  }

  public Validator getValidator() {
    return validator;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public Set<Integer> getCommitteeIndices() {
    return committeeIndices;
  }
}
