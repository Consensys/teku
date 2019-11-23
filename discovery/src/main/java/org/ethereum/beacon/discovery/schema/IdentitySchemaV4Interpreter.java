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
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;

public class IdentitySchemaV4Interpreter implements IdentitySchemaInterpreter {
  @Override
  public void verify(NodeRecord nodeRecord) {
    IdentitySchemaInterpreter.super.verify(nodeRecord);
    if (nodeRecord.get(EnrFieldV4.PKEY_SECP256K1) == null) {
      throw new RuntimeException(
          String.format(
              "Field %s not exists but required for scheme %s",
              EnrFieldV4.PKEY_SECP256K1, getScheme()));
    }
    Bytes pubKey = (Bytes) nodeRecord.get(EnrFieldV4.PKEY_SECP256K1); // compressed
    assert Functions.verifyECDSASignature(
        nodeRecord.getSignature(), Functions.hashKeccak(nodeRecord.serializeNoSignature()), pubKey);
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.V4;
  }

  @Override
  public Bytes getNodeId(NodeRecord nodeRecord) {
    verify(nodeRecord);
    Bytes pkey = (Bytes) nodeRecord.getKey(EnrFieldV4.PKEY_SECP256K1);
    ECPoint pudDestPoint = Functions.publicKeyToPoint(pkey);
    Bytes xPart =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(pudDestPoint.getXCoord().toBigInteger(), 32));
    Bytes yPart =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(pudDestPoint.getYCoord().toBigInteger(), 32));
    return Functions.hashKeccak(Bytes.concatenate(xPart, yPart));
  }

  @Override
  public void sign(NodeRecord nodeRecord, Object signOptions) {
    Bytes privateKey = (Bytes) signOptions;
    Bytes signature =
        Functions.sign(privateKey, Functions.hashKeccak(nodeRecord.serializeNoSignature()));
    nodeRecord.setSignature(signature);
  }
}
