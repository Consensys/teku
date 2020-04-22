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

package tech.pegasys.artemis.validator.client.duties;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.ForkProvider;
import tech.pegasys.artemis.validator.client.Validator;

public class ValidatorDutyFactory {
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;

  public ValidatorDutyFactory(
      final ForkProvider forkProvider, final ValidatorApiChannel validatorApiChannel) {
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
  }

  public BlockProductionDuty createBlockProductionDuty(
      final UnsignedLong slot, final Validator validator) {
    return new BlockProductionDuty(validator, slot, forkProvider, validatorApiChannel);
  }

  public AttestationProductionDuty createAttestationProductionDuty(final UnsignedLong slot) {
    return new AttestationProductionDuty(slot, forkProvider, validatorApiChannel);
  }

  public AggregationDuty createAggregationDuty(final UnsignedLong slot) {
    return new AggregationDuty(slot, validatorApiChannel, forkProvider);
  }
}
