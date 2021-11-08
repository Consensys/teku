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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;

public class DeleteKeyResult {
  private final DeletionStatus status;
  private final Optional<String> message;
  private final Optional<String> slashingProtection;

  @VisibleForTesting
  DeleteKeyResult(
      final DeletionStatus status,
      final Optional<String> message,
      final Optional<String> slashingProtection) {
    this.status = status;
    this.message = message;
    this.slashingProtection = slashingProtection;
  }

  public static DeleteKeyResult success(final String slashingProtection) {
    return new DeleteKeyResult(
        DeletionStatus.DELETED, Optional.empty(), Optional.of(slashingProtection));
  }

  public static DeleteKeyResult notFound() {
    return new DeleteKeyResult(DeletionStatus.NOT_FOUND, Optional.empty(), Optional.empty());
  }

  public static DeleteKeyResult notActive(final String slashingProtection) {
    return new DeleteKeyResult(
        DeletionStatus.NOT_ACTIVE, Optional.empty(), Optional.of(slashingProtection));
  }

  public static DeleteKeyResult error(final String message) {
    return new DeleteKeyResult(DeletionStatus.ERROR, Optional.of(message), Optional.empty());
  }

  public DeletionStatus getStatus() {
    return status;
  }

  public Optional<String> getMessage() {
    return message;
  }

  public Optional<String> getSlashingProtection() {
    return slashingProtection;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DeleteKeyResult that = (DeleteKeyResult) o;
    return status == that.status
        && Objects.equals(message, that.message)
        && Objects.equals(slashingProtection, that.slashingProtection);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, message, slashingProtection);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("message", message)
        .add("slashingProtection", slashingProtection)
        .toString();
  }
}
