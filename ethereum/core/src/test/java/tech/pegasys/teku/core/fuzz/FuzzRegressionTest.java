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

package tech.pegasys.teku.core.fuzz;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.net.URL;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.BlockProcessorUtil;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class FuzzRegressionTest {
  @Test
  void shouldRejectAttesterSlashingWithInvalidValidatorIndex() throws Exception {
    final BeaconState state = load("issue2345/state.ssz", BeaconStateImpl.class);
    final AttesterSlashing slashing =
        load("issue2345/attester_slashing.ssz", AttesterSlashing.class);

    assertThatThrownBy(
            () ->
                state.updated(
                    mutableState ->
                        BlockProcessorUtil.process_attester_slashings(
                            mutableState, SSZList.singleton(slashing))))
        .isInstanceOf(BlockProcessingException.class);
  }

  private <T> T load(final String resource, final Class<T> type) throws Exception {
    final URL resourceUrl = FuzzRegressionTest.class.getResource(resource);
    final Bytes data = Bytes.wrap(Resources.toByteArray(resourceUrl));
    return SimpleOffsetSerializer.deserialize(data, type);
  }
}
