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

import java.util.Map;
import java.util.function.Supplier;

/**
 * Any class which wants to support SSZ incremental hashing or incremental serialization should
 * implement this interface
 *
 * <p>This interface supports tracking of changes inside a SZZ List or Container to make it possible
 * to recalculate the trie hash for updated chunks only
 *
 * <p>If a children of this List or a member of this Container also implements {@link
 * ObservableComposite} interface it should notify its parent on his own updates via {@link
 * UpdateListener#childUpdated(int)} Else the incremental hashing would be incorrectly calculated.
 *
 * <p>Observable instances can be copied and the changes can be made in both copies independently In
 * this case each Observable copy should independently manage its own changes. For this each
 * installed listener should be 'forked' to a created copy with {@link UpdateListener#fork()}
 */
public interface ObservableComposite {

  /**
   * Returns an {@link UpdateListener} corresponding to the supplied observerId. If the listener for
   * this observerId is not yet installed then it should be created by the supplied listenerFactory.
   * Normally the {@link ObservableComposite} should maintain a <code>Map&lt;String, UpdateListener>
   * </code> mapping for keep tracking of listeners for all observerId's and notify all of them. See
   * {@link ObservableCompositeHelper} which conveniently encapsulates this functionality
   *
   * @param observerId String ID of an observer
   * @param listenerFactory listener creator if the listener is missing
   * @return already stored or just created with listenerFactory listener instance
   */
  UpdateListener getUpdateListener(String observerId, Supplier<UpdateListener> listenerFactory);

  /** Returns the full map of all installed listeners */
  Map<String, UpdateListener> getAllUpdateListeners();
}
