/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.executionlayer;

public enum ExecutionPayloadStatus {
  VALID(Validity.VALID),
  INVALID_INCLUSION_LIST(Validity.INVALID),
  INVALID(Validity.INVALID),
  SYNCING(Validity.NOT_VALIDATED),
  ACCEPTED(Validity.NOT_VALIDATED);

  private final Validity validity;

  ExecutionPayloadStatus(final Validity validity) {
    this.validity = validity;
  }

  public boolean isValid() {
    return validity == Validity.VALID;
  }

  public boolean isNotValidated() {
    return validity == Validity.NOT_VALIDATED;
  }

  public boolean isInvalid() {
    return validity == Validity.INVALID;
  }

  public boolean hasInvalidInclusionList() {
    return this.equals(INVALID_INCLUSION_LIST);
  }

  private enum Validity {
    VALID,
    NOT_VALIDATED,
    INVALID
  }
}
