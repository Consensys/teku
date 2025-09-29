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

package tech.pegasys.teku.storage.server;

import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.statetransition.api.CustodyGroupCountChannel;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.VersionedHashDBSource;

public class VersionedHashDBSourceFactory {
  private final Function<BlobSidecar, VersionedHash> blobSidecarToVersionedHash;
  private final Function<Pair<DataColumnSidecar, UInt64>, VersionedHash>
      dataColumnSidecarToVersionedHash;
  private final EventChannels eventChannels;
  private final Spec spec;

  public VersionedHashDBSourceFactory(final Spec spec, final EventChannels eventChannels) {
    this.spec = spec;
    this.eventChannels = eventChannels;
    this.blobSidecarToVersionedHash =
        blobSidecar ->
            MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers())
                .kzgCommitmentToVersionedHash(blobSidecar.getKZGCommitment());
    this.dataColumnSidecarToVersionedHash =
        pair ->
            MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers())
                .kzgCommitmentToVersionedHash(
                    pair.getKey()
                        .getKzgCommitments()
                        .get(pair.getValue().intValue())
                        .getKZGCommitment());
  }

  public VersionedHashDBSource createVersionedHashDBSource(final KvStoreCombinedDao dao) {
    final VersionedHashDBSource versionedHashDBSource =
        new VersionedHashDBSource(
            dao, blobSidecarToVersionedHash, dataColumnSidecarToVersionedHash, spec);
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      eventChannels.subscribe(
          CustodyGroupCountChannel.class,
          new CustodyGroupCountChannel() {
            @Override
            public void onGroupCountUpdate(
                final int custodyGroupCount, final int samplingGroupCount) {
              final int numberOfColumns =
                  SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
                      .getNumberOfColumns();
              if (custodyGroupCount == numberOfColumns) {
                versionedHashDBSource.storeSidecarHashes();
              }
            }

            @Override
            public void onCustodyGroupCountSynced(final int groupCount) {}
          });
    }

    return versionedHashDBSource;
  }
}
