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

package tech.pegasys.teku.statetransition.validation;

import com.google.common.base.MoreObjects;

import java.util.Objects;
import java.util.Optional;

public class InternalValidationResult {

  private final ValidationResultCode validationResultCode;
  private final Optional<String> description;

  private InternalValidationResult(
      final ValidationResultCode validationResultCode, final Optional<String> description) {
    this.validationResultCode = validationResultCode;
    this.description = description;
  }

  public static InternalValidationResult create(final ValidationResultCode validationResultCode) {
    return new InternalValidationResult(validationResultCode, Optional.empty());
  }

  public static InternalValidationResult create(
      final ValidationResultCode validationResultCode, final String description) {
    return new InternalValidationResult(validationResultCode, Optional.of(description));
  }

  public ValidationResultCode code() {
    return validationResultCode;
  }

  public Optional<String> getDescription() {
    return description;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final InternalValidationResult that = (InternalValidationResult) o;
    return validationResultCode == that.validationResultCode
        && Objects.equals(description, that.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validationResultCode, description);
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this)
        .add("validationResultCode", validationResultCode);

    if (description.isPresent()) {
      helper.add("description", description.get());
    }

    return helper.toString();
  }
}
