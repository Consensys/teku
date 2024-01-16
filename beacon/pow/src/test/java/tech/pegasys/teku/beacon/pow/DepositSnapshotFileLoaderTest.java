/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.beacon.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertWith;

import java.math.BigInteger;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.schema.LoadDepositSnapshotResult;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositSnapshotFileLoaderTest {

  private static final String SNAPSHOT_BUNDLED_RESOURCE = "deposit_tree_snapshot_bundled.ssz";
  private static final String SNAPSHOT_BEACON_API_RESOURCE = "deposit_tree_snapshot_beaconAPI.json";

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private String notFoundResource;
  private DepositSnapshotFileLoader depositSnapshotLoader;

  @BeforeEach
  public void setup() {
    //    this.notFoundResource = dataStructureUtil.randomBytes32().toHexString();
    this.depositSnapshotLoader =
        new DepositSnapshotFileLoader(List.of(getResourceFilePath(SNAPSHOT_BUNDLED_RESOURCE)));
  }

  @Test
  public void shouldReturnEmpty_whenNoSourceForDepositSnapshotIsProvided() {
    this.depositSnapshotLoader = new DepositSnapshotFileLoader(List.of());
    final LoadDepositSnapshotResult result = depositSnapshotLoader.loadDepositSnapshot();
    assertThat(result.getDepositTreeSnapshot()).isEmpty();
  }

  @Test
  public void shouldThrowInvalidConfigurationException_whenIncorrectResourcePath() {
    this.depositSnapshotLoader = new DepositSnapshotFileLoader(List.of(notFoundResource));
    assertThatThrownBy(() -> depositSnapshotLoader.loadDepositSnapshot())
        .isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldHaveCorrectDepositsData_whenSnapshotLoaded() {
    final int deposits = 16646;
    final int blockNumber = 1033803;
    final LoadDepositSnapshotResult result = depositSnapshotLoader.loadDepositSnapshot();
    assertWith(
        result,
        snapshotResult -> {
          assertThat(snapshotResult.getDepositTreeSnapshot().isPresent()).isTrue();
          assertThat(snapshotResult.getDepositTreeSnapshot().get().getDepositCount())
              .isEqualTo(deposits);
          assertThat(snapshotResult.getReplayDepositsResult().getLastProcessedDepositIndex())
              .contains(BigInteger.valueOf(deposits - 1));
          assertThat(snapshotResult.getReplayDepositsResult().getLastProcessedBlockNumber())
              .isEqualTo(blockNumber);
        });
  }

  @Test
  public void shouldTryAllAvailableSources() {
    final String validResourcePath = getResourceFilePath(SNAPSHOT_BEACON_API_RESOURCE);
    depositSnapshotLoader = new DepositSnapshotFileLoader(List.of("/foo/empty", validResourcePath));

    final LoadDepositSnapshotResult result = depositSnapshotLoader.loadDepositSnapshot();
    assertThat(result.getDepositTreeSnapshot()).isPresent();
  }

  @Test
  public void shouldReturnEmptyIfNoSourceIsAvailable() {
    depositSnapshotLoader = new DepositSnapshotFileLoader(List.of("/foo/empty", "/foo/empty2"));

    final LoadDepositSnapshotResult result = depositSnapshotLoader.loadDepositSnapshot();
    assertThat(result.getDepositTreeSnapshot()).isEmpty();
  }

  @Test
  public void shouldFallbackToJsonDeserialization() {
    // From deposit_tree_snapshot_beaconAPI.json file
    final int deposits = 1106572;
    final int blockNumber = 18754822;

    depositSnapshotLoader =
        new DepositSnapshotFileLoader(List.of(getResourceFilePath(SNAPSHOT_BEACON_API_RESOURCE)));
    final LoadDepositSnapshotResult result = depositSnapshotLoader.loadDepositSnapshot();

    assertWith(
        result,
        snapshotResult -> {
          assertThat(snapshotResult.getDepositTreeSnapshot().isPresent()).isTrue();
          assertThat(snapshotResult.getDepositTreeSnapshot().get().getDepositCount())
              .isEqualTo(deposits);
          assertThat(snapshotResult.getReplayDepositsResult().getLastProcessedDepositIndex())
              .contains(BigInteger.valueOf(deposits - 1));
          assertThat(snapshotResult.getReplayDepositsResult().getLastProcessedBlockNumber())
              .isEqualTo(blockNumber);
        });
  }

  private String getResourceFilePath(final String resource) {
    final URL resourceUrl = DepositSnapshotFileLoader.class.getResource(resource);
    return resourceUrl.getFile();
  }
}
