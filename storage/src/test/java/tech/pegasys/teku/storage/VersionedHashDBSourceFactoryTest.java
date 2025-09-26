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

package tech.pegasys.teku.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.events.ChannelExceptionHandler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.statetransition.api.CustodyGroupCountChannel;
import tech.pegasys.teku.storage.server.VersionedHashDBSourceFactory;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.VersionedHashDBSource;

public class VersionedHashDBSourceFactoryTest {
  @Test
  public void activatesDataColumnSidecarStoreHashesForSupernode() {
    final Spec spec = TestSpecFactory.createMinimalFulu();
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(
            ChannelExceptionHandler.THROWING_HANDLER, new NoOpMetricsSystem());
    final VersionedHashDBSourceFactory versionedHashDBSourceFactory =
        new VersionedHashDBSourceFactory(spec, eventChannels);

    final VersionedHashDBSource versionedHashDBSource =
        versionedHashDBSourceFactory.createVersionedHashDBSource(mock(KvStoreCombinedDao.class));
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isFalse();

    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
    eventChannels
        .getPublisher(CustodyGroupCountChannel.class)
        .onGroupCountUpdate(
            specConfigFulu.getNumberOfCustodyGroups(), specConfigFulu.getNumberOfCustodyGroups());
    assertThat(versionedHashDBSource.isStoreSidecarHashes()).isTrue();
  }
}
