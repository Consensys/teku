/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.rocksdb;

import org.rocksdb.RocksDBException;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class RocksDbExceptionUtil {
  public static DatabaseStorageException wrapException(
      final String message, final RocksDBException cause) {
    return isUnrecoverable(cause)
        ? DatabaseStorageException.unrecoverable(message, cause)
        : DatabaseStorageException.recoverable(message, cause);
  }

  private static boolean isUnrecoverable(final RocksDBException cause) {
    if (cause.getStatus() == null) {
      return true;
    }
    switch (cause.getStatus().getCode()) {
      case TimedOut:
      case TryAgain:
      case MergeInProgress:
      case Incomplete:
      case Busy:
      case Expired:
        return false;
      default:
        return true;
    }
  }
}
