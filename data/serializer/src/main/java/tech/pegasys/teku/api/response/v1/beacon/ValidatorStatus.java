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

package tech.pegasys.teku.api.response.v1.beacon;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "[Validator status specification](https://hackmd.io/ofFJ5gOmQpu1jjHilHbdQQ)")
public enum ValidatorStatus {
  pending_initialized,
  pending_queued,
  active_ongoing,
  active_exiting,
  active_slashed,
  exited_unslashed,
  exited_slashed,
  withdrawal_possible,
  withdrawal_done
}
