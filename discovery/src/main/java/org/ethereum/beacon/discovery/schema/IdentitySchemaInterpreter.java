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

package org.ethereum.beacon.discovery.schema;

import org.apache.tuweni.bytes.Bytes;

/**
 * Interprets identity schema of ethereum node record:
 *
 * <ul>
 *   <li>derives node id from node record
 *   <li>>signs node record
 *   <li>verifies signature of node record
 * </ul>
 */
public interface IdentitySchemaInterpreter {
  /** Returns supported scheme */
  IdentitySchema getScheme();

  /* Signs nodeRecord, modifying it */
  void sign(NodeRecord nodeRecord, Object signOptions);

  /** Verifies that `nodeRecord` is of scheme implementation */
  default void verify(NodeRecord nodeRecord) {
    if (!nodeRecord.getIdentityScheme().equals(getScheme())) {
      throw new RuntimeException("Interpreter and node record schemes do not match!");
    }
  }

  /** Delivers nodeId according to identity scheme scheme */
  Bytes getNodeId(NodeRecord nodeRecord);
}
