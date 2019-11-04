/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.discovery;

import java.util.Map;

/**
 * {@link DataSource} which supports batch updates. Batch update can be either atomic or not
 * depending on the implementation
 */
public interface BatchUpdateDataSource<KeyType, ValueType> extends DataSource<KeyType, ValueType> {

  /**
   * Applies passed updates to this source. If the implementing class supports atomic updates, then
   * the changes passed shouldn't be visible partially to a code querying this source from another
   * thread
   *
   * @param updates the Map should be treated as unmodifiable Collection of Key-Value pairs with
   *     unique Keys. A pair with non-null value represents <code>put</code> operation A pair with
   *     <code>null</code> value represents <code>remove</code> operation
   */
  void batchUpdate(Map<KeyType, ValueType> updates);
}
