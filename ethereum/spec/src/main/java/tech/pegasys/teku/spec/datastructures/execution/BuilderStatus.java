/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.execution;

import com.google.common.base.MoreObjects;

public class BuilderStatus {

  private final Status status;
  private final String errorMessage;

  private BuilderStatus(final Status status, final String errorMessage) {
    this.status = status;
    this.errorMessage = errorMessage;
  }

  public static BuilderStatus withOkStatus() {
    return new BuilderStatus(Status.OK, null);
  }

  public static BuilderStatus withFailedStatus(final String errorMessage) {
    return new BuilderStatus(null, errorMessage);
  }

  public static BuilderStatus withFailedStatus(final Throwable throwable) {
    return withFailedStatus(throwable.getMessage());
  }

  public Status getStatus() {
    return status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public boolean hasFailed() {
    return status == null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("errorMessage", errorMessage)
        .toString();
  }

  public enum Status {
    OK
  }
}
