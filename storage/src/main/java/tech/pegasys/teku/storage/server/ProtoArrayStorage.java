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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;

public class ProtoArrayStorage implements ProtoArrayStorageChannel {
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
    return SafeFuture.completedFuture(database.getProtoArraySnapshot());
  }
}
