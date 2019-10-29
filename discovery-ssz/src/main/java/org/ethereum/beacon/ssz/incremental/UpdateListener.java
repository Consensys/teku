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

package org.ethereum.beacon.ssz.incremental;

/** A listener which is notified on changes in {@link ObservableComposite} */
public interface UpdateListener {

  /**
   * Notifies that the child with specified index was updated, removed or added. E.g. if an element
   * with index 3 is removed from a list of size 5, then this method is expected to be called 3
   * times with values <code>3, 4, 5</code>
   */
  void childUpdated(int childIndex);

  /**
   * Creates an independent copy of this {@link UpdateListener} which will be tracking updates
   * independently of this listener updates. This is done to support {@link ObservableComposite}
   * instances copying. When forking both {@link UpdateListener} copies should have the same changes
   * accumulated before the fork.
   */
  UpdateListener fork();
}
