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

package tech.pegasys.teku.storage.server;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;

public class ProtoArrayStorage implements ProtoArrayStorageChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final Database database;

  public ProtoArrayStorage(Database database) {
    this.database = database;
  }

  @Override
  public void onProtoArrayUpdate(ProtoArraySnapshot protoArraySnapshot) {
    database.putProtoArraySnapshot(protoArraySnapshot);
  }

  @Override
  public SafeFuture<Optional<ProtoArraySnapshot>> getProtoArraySnapshot() {
    LOG.trace("Loading protoarray snapshot");
    final Optional<ProtoArraySnapshot> result = database.getProtoArraySnapshot();
    LOG.trace("Loaded protoarray snapshot");
    return SafeFuture.completedFuture(result);
  }
}
