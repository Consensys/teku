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
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The helper class which encapsulates {@link ObservableComposite} functionality */
public class ObservableCompositeHelper implements UpdateListener, ObservableComposite {

  private static final String PARENT_OBSERVER_ID = "parent";

  /**
   * Convenient wrapper for a value inside a Container. Created to avoid manual field update
   * notifying. Assigning a new field value is done via {@link #set(Object)} method which
   * automatically notifies installed listeners on the child update with the right child index
   *
   * @see #newValue(Object)
   */
  public class ObsValue<C> {
    private C value;
    private final int index;

    public ObsValue(C value, int index) {
      this.index = index;
      set(value);
    }

    /**
     * Sets the wrapper value. If the value is an instance of {@link ObservableComposite} then a
     * special listener is added to this value to notify the parent container
     */
    public void set(C val) {
      if (value instanceof ObservableComposite && !(val instanceof ObservableComposite)) {
        throw new IllegalArgumentException(
            "An attempt to override observable value with non-observable");
      }
      if (val instanceof ObservableComposite) {
        ((ObservableComposite) val)
            .getUpdateListener(
                PARENT_OBSERVER_ID,
                () ->
                    new UpdateListener() {
                      @Override
                      public void childUpdated(int childIndex) {
                        ObservableCompositeHelper.this.childUpdated(index);
                      }

                      @Override
                      public UpdateListener fork() {
                        return this;
                      }
                    });
      }
      value = val;
      childUpdated(index);
    }

    public C get() {
      return value;
    }

    @Override
    public String toString() {
      return value == null ? "null" : value.toString();
    }
  }

  private Map<String, UpdateListener> listeners;
  private int childCounter = 0;

  public ObservableCompositeHelper() {
    this(new ConcurrentHashMap<>());
  }

  public ObservableCompositeHelper(Map<String, UpdateListener> listeners) {
    this.listeners = listeners;
  }

  @Override
  public UpdateListener getUpdateListener(
      String observerId, Supplier<UpdateListener> listenerFactory) {
    return listeners.computeIfAbsent(observerId, s -> listenerFactory.get());
  }

  @Override
  public ObservableCompositeHelper fork() {
    return new ObservableCompositeHelper(copyListeners());
  }

  public void addAllListeners(Map<String, UpdateListener> listeners) {
    this.listeners.putAll(listeners);
  }

  @Override
  public Map<String, UpdateListener> getAllUpdateListeners() {
    return copyListeners();
  }

  private Map<String, UpdateListener> copyListeners() {
    Map<String, UpdateListener> lCopies = new ConcurrentHashMap<>();
    for (Entry<String, UpdateListener> entry : listeners.entrySet()) {
      if (!PARENT_OBSERVER_ID.equals(entry.getKey())) {
        lCopies.put(entry.getKey(), entry.getValue().fork());
      }
    }
    return lCopies;
  }

  @Override
  public void childUpdated(int childIndex) {
    listeners.values().forEach(l -> l.childUpdated(childIndex));
  }

  public void childrenUpdated(int fromIdx, int count) {
    for (int i = 0; i < count; i++) {
      childUpdated(fromIdx + i);
    }
  }

  /**
   * Creates a container field wrapper. The field wrappers should be created in the order of
   * corresponding SSZ container members to maintain the correct member index. Class fields can be
   * declared the following way:
   *
   * <pre>
   *   ObservableCompositeHelper helper = new ObservableCompositeHelper();
   *   ObsValue<UInt64> member_0 = helper.newValue(INITIAL_VALUE_0);
   *   ObsValue<UInt64> member_1 = helper.newValue(INITIAL_VALUE_1);
   * </pre>
   */
  public <C> ObsValue<C> newValue(C initialValue) {
    return new ObsValue<>(initialValue, childCounter++);
  }
}
