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

package tech.pegasys.teku.services.chainstorage;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.kvstore.BlindedBlockMigration;

public class BlindedBlockMigrationService extends Service {
  private final AsyncRunner asyncRunner;
  private final Optional<BlindedBlockMigration<?>> maybeMigrater;
  private SafeFuture<Void> executor;

  public BlindedBlockMigrationService(final AsyncRunner asyncRunner, final Database database) {
    this.asyncRunner = asyncRunner;
    this.maybeMigrater = database.getBlindedBlockMigrater();
  }

  @Override
  protected SafeFuture<?> doStart() {
    if (maybeMigrater.isPresent()) {
      executor = asyncRunner.runAsync(() -> maybeMigrater.get().migrateBlocks());
    } else {
      executor = SafeFuture.COMPLETE;
    }
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    if (!executor.isDone()) {
      maybeMigrater.ifPresent(BlindedBlockMigration::terminate);
    }
    return executor;
  }
}
