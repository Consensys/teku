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

package org.ethereum.beacon.discovery.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Container for any kind of objects used in packet-messages-tasks flow */
public class Envelope {
  private UUID id;
  private Map<Field, Object> data = new HashMap<>();

  public Envelope() {
    this.id = UUID.randomUUID();
  }

  public synchronized void put(Field key, Object value) {
    data.put(key, value);
  }

  public synchronized Object get(Field key) {
    return data.get(key);
  }

  public synchronized boolean remove(Field key) {
    return data.remove(key) != null;
  }

  public synchronized boolean contains(Field key) {
    return data.containsKey(key);
  }

  public UUID getId() {
    return id;
  }
}
