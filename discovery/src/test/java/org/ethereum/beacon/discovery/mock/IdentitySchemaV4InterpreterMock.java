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

package org.ethereum.beacon.discovery.mock;

import org.apache.tuweni.bytes.MutableBytes;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class IdentitySchemaV4InterpreterMock extends IdentitySchemaV4Interpreter {
  @Override
  public void verify(NodeRecord nodeRecord) {
    // Don't verify signature
  }

  @Override
  public void sign(NodeRecord nodeRecord, Object signOptions) {
    nodeRecord.setSignature(MutableBytes.create(96));
  }
}
