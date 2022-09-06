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

package tech.pegasys.teku.api.request.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconCommitteeSubscriptionRequestTest {

  @Test
  public void shouldFailInitializingIfValidatorIndexIsNull() {
    final NullPointerException exception =
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new BeaconCommitteeSubscriptionRequest(null, "1", UInt64.ONE, UInt64.ONE, true));
    assertThat(exception).hasMessage("validator_index should be specified");
  }

  @Test
  public void shouldFailInitializingIfCommitteeIndexIsNull() {
    final NullPointerException exception =
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new BeaconCommitteeSubscriptionRequest("1", null, UInt64.ONE, UInt64.ONE, true));
    assertThat(exception).hasMessage("committee_index should be specified");
  }
}
