/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.storage.archive;

import java.io.Closeable;

/**
 * An interface to allow storing data that is to be pruned from the Database. If the store function
 * is successful it returns true, signalling the data can be pruned. If the store function fails,
 * the data was not stored and the data should not be pruned.
 *
 * @param <T> the data to be stored.
 */
public interface DataArchiveWriter<T> extends Closeable {
  boolean archive(final T data);
}
