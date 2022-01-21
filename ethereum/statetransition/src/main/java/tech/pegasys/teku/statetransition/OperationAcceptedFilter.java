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

package tech.pegasys.teku.statetransition;

import java.util.function.Consumer;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class OperationAcceptedFilter<T> implements OperationAddedSubscriber<T> {

  private final Consumer<T> delegate;

  public OperationAcceptedFilter(final Consumer<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onOperationAdded(final T operation, final InternalValidationResult validationStatus) {
    if (validationStatus.code() == ValidationResultCode.ACCEPT) {
      delegate.accept(operation);
    }
  }
}
