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

package tech.pegasys.teku.spec.datastructures.execution.versions.capella;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public interface ExecutionPayloadCapella extends ExecutionPayload {

  SszList<Withdrawal> getWithdrawals();

  @Override
  default Optional<SszList<Withdrawal>> getOptionalWithdrawals() {
    return Optional.of(getWithdrawals());
  }

  @Override
  default Optional<ExecutionPayloadCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
