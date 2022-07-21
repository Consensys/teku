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

package tech.pegasys.teku.services.powchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.beacon.pow.Eth1Provider;
import tech.pegasys.teku.ethereum.pow.api.schema.LoadDepositSnapshotResult;
import tech.pegasys.teku.ethereum.pow.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositSnapshotLoaderTest {
  private static final String SNAPSHOT_RESOURCE = "snapshot.ssz";

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);

  private String notFoundResource;
  private DepositSnapshotLoader depositSnapshotLoader;

  @BeforeEach
  public void setup() {
    this.notFoundResource = dataStructureUtil.randomBytes32().toHexString();
    this.depositSnapshotLoader =
        new DepositSnapshotLoader(
            Optional.of(getResourceFilePath(SNAPSHOT_RESOURCE)), eth1Provider);
  }

  @Test
  public void shouldReturnEmpty_whenNoDepositSnapshot() throws Exception {
    this.depositSnapshotLoader = new DepositSnapshotLoader(Optional.empty(), eth1Provider);
    final SafeFuture<LoadDepositSnapshotResult> result =
        depositSnapshotLoader.loadDepositSnapshot();
    assertThat(result.get().getDepositTreeSnapshot()).isEmpty();
    verifyNoInteractions(eth1Provider);
  }

  @Test
  public void shouldThrowInvalidConfigurationException_whenIncorrectResourcePath() {
    this.depositSnapshotLoader =
        new DepositSnapshotLoader(Optional.of(notFoundResource), eth1Provider);
    final SafeFuture<LoadDepositSnapshotResult> result =
        depositSnapshotLoader.loadDepositSnapshot();
    assertThatThrownBy(result::get).cause().isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldReturnEmptyResult_whenELIsDown() throws Exception {
    when(eth1Provider.getGuaranteedEth1Block(any(String.class)))
        .thenReturn(SafeFuture.failedFuture(new Error("")));
    final SafeFuture<LoadDepositSnapshotResult> result =
        depositSnapshotLoader.loadDepositSnapshot();
    assertWith(
        result.get(),
        snapshotResult -> {
          assertThat(snapshotResult.getDepositTreeSnapshot().isEmpty()).isTrue();
          assertThat(snapshotResult.getReplayDepositsResult())
              .isEqualTo(ReplayDepositsResult.empty());
        });
  }

  @Test
  public void shouldHaveCorrectDepositsData_whenAllServicesAvailable() throws Exception {
    final EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getNumber()).thenReturn(BigInteger.valueOf(100));
    when(eth1Provider.getGuaranteedEth1Block(any(String.class)))
        .thenReturn(SafeFuture.completedFuture(block));
    final SafeFuture<LoadDepositSnapshotResult> result =
        depositSnapshotLoader.loadDepositSnapshot();
    assertWith(
        result.get(),
        snapshotResult -> {
          assertThat(snapshotResult.getDepositTreeSnapshot().isPresent()).isTrue();
          assertThat(snapshotResult.getDepositTreeSnapshot().get().getDeposits()).isEqualTo(16430);
          assertThat(snapshotResult.getReplayDepositsResult().getLastProcessedDepositIndex())
              .contains(BigInteger.valueOf(16429));
          assertThat(snapshotResult.getReplayDepositsResult().getLastProcessedBlockNumber())
              .isEqualTo(100);
        });
  }

  private String getResourceFilePath(final String resource) {
    final URL resourceUrl = DepositSnapshotResourceLoader.class.getResource(resource);
    return resourceUrl.getFile();
  }
}
