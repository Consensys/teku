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

package org.ethereum.beacon.crypto.bls.codec;

import java.math.BigInteger;
import org.ethereum.beacon.crypto.bls.bc.BCParameters;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Validates byte sequence against point format described in the spec <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations">https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations</a>
 *
 * @see PointData
 * @see Codec
 */
public interface Validator {

  /**
   * Runs validations.
   *
   * @param encoded byte sequence that represents and encoded point.
   * @return result of the validation.
   * @see Result
   */
  Result validate(BytesValue encoded);

  /**
   * Validator for <code>G<sub>1</sub></code> points.
   *
   * @see PointData.G1
   */
  Validator G1 =
      new Validator() {
        @Override
        public Result validate(BytesValue encoded) {
          if (encoded.size() != Bytes48.SIZE) {
            return Result.invalid(
                String.format(
                    "unexpected length of encoded G1, should be %d got %d",
                    Bytes48.SIZE, encoded.size()));
          }

          PointData.G1 data = Codec.G1.decode(Bytes48.wrap(encoded, 0));

          int aFlag = data.getFlags().test(Flags.A);
          int bFlag = data.getFlags().test(Flags.B);
          int cFlag = data.getFlags().test(Flags.C);
          BigInteger x = new BigInteger(1, data.getX());

          if (cFlag != 1) {
            return Result.invalid("invalid c_flag, should always be 1 but got " + cFlag);
          }

          if (bFlag == 1) {
            if (aFlag != 0) {
              return Result.invalid(
                  "invalid a_flag, should be 0 when b_flag == 1 but got " + aFlag);
            }
            if (x.compareTo(BigInteger.ZERO) != 0) {
              return Result.invalid("invalid x, should be 0 when b_flag == 1 but got " + x);
            }
          }

          if (BCParameters.Q.compareTo(x) <= 0) {
            return Result.invalid("invalid x, should be < Q");
          }

          return Result.Valid;
        }
      };

  /**
   * Validator for <code>G<sub>2</sub></code> points.
   *
   * @see PointData.G2
   */
  Validator G2 =
      new Validator() {
        @Override
        public Result validate(BytesValue encoded) {
          if (encoded.size() != Bytes96.SIZE) {
            return Result.invalid(
                String.format(
                    "unexpected length of encoded G2, should be %d got %d",
                    Bytes96.SIZE, encoded.size()));
          }

          PointData.G2 data = Codec.G2.decode(Bytes96.wrap(encoded, 0));

          int aFlag = data.getFlags1().test(Flags.A);
          int bFlag = data.getFlags1().test(Flags.B);
          int cFlag = data.getFlags1().test(Flags.C);
          Flags flags2 = data.getFlags2();
          BigInteger x1 = new BigInteger(1, data.getX1());
          BigInteger x2 = new BigInteger(1, data.getX2());

          if (!flags2.isZero()) {
            return Result.invalid("invalid flag2 value, should always be 0");
          }

          if (cFlag != 1) {
            return Result.invalid("invalid c_flag, should always be 1 but got " + cFlag);
          }

          if (bFlag == 1) {
            if (aFlag != 0) {
              return Result.invalid(
                  "invalid a_flag, should be 0 when b_flag == 1 but got " + aFlag);
            }
            if (x1.compareTo(BigInteger.ZERO) != 0) {
              return Result.invalid("invalid x1, should be 0 when b_flag == 1 but got " + x1);
            }
            if (x2.compareTo(BigInteger.ZERO) != 0) {
              return Result.invalid("invalid x2, should be 0 when b_flag == 1 but got " + x2);
            }
          }

          if (BCParameters.Q.compareTo(x1) <= 0) {
            return Result.invalid("invalid x1, should be < Q");
          }
          if (BCParameters.Q.compareTo(x2) <= 0) {
            return Result.invalid("invalid x2, should be < Q");
          }

          return Result.Valid;
        }
      };

  /**
   * Keeps result of the validation.
   *
   * <p>Contains a flag and a message describing an error.
   */
  class Result {
    private static final Result Valid = new Result(true, "");

    final boolean valid;
    final String message;

    Result(boolean valid, String message) {
      this.valid = valid;
      this.message = message;
    }

    static Result invalid(String message) {
      return new Result(false, message);
    }

    public boolean isValid() {
      return valid;
    }

    public String getMessage() {
      return message;
    }
  }
}
