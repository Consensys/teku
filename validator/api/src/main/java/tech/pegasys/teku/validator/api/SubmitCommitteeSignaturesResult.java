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

package tech.pegasys.teku.validator.api;

import java.util.List;
import java.util.Objects;

public class SubmitCommitteeSignaturesResult {
  private final List<SubmitCommitteeSignatureError> errors;

  public SubmitCommitteeSignaturesResult(final List<SubmitCommitteeSignatureError> errors) {
    this.errors = errors;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SubmitCommitteeSignaturesResult that = (SubmitCommitteeSignaturesResult) o;
    return Objects.equals(errors, that.errors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(errors);
  }

  public List<SubmitCommitteeSignatureError> getErrors() {
    return errors;
  }
}
