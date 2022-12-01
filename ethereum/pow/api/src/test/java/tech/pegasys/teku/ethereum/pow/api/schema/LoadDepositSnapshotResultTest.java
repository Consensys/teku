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

package tech.pegasys.teku.ethereum.pow.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class LoadDepositSnapshotResultTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void shouldMaxNonEmpty() {
    final LoadDepositSnapshotResult emptyResult = LoadDepositSnapshotResult.EMPTY;
    final LoadDepositSnapshotResult snapshotResult =
        LoadDepositSnapshotResult.create(
            Optional.of(dataStructureUtil.randomDepositTreeSnapshot()));
    assertThat(ObjectUtils.max(emptyResult, snapshotResult)).isEqualTo(snapshotResult);
    assertThat(ObjectUtils.max(snapshotResult, emptyResult)).isEqualTo(snapshotResult);
  }

  @Test
  public void shouldMaxHighestHeight() {
    final LoadDepositSnapshotResult snapshotResultMin =
        LoadDepositSnapshotResult.create(
            Optional.of(dataStructureUtil.randomDepositTreeSnapshot(10, UInt64.valueOf(10))));
    final LoadDepositSnapshotResult snapshotResultMax =
        LoadDepositSnapshotResult.create(
            Optional.of(dataStructureUtil.randomDepositTreeSnapshot(10, UInt64.valueOf(12))));
    assertThat(ObjectUtils.max(snapshotResultMax, snapshotResultMin)).isEqualTo(snapshotResultMax);
    assertThat(ObjectUtils.max(snapshotResultMin, snapshotResultMax)).isEqualTo(snapshotResultMax);
  }
}
