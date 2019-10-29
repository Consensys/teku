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

package org.ethereum.beacon.ssz.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.ethereum.beacon.ssz.access.container.SSZAnnotationSchemeBuilder;

/**
 * SSZ model field annotation. Used at model field definition.
 *
 * <p>Clarifies SSZ encoding/decoding details
 *
 * <p>Required if {@link SSZAnnotationSchemeBuilder} `explicitFieldAnnotation` is set to
 * <b>true</b>, otherwise it's optional.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface SSZ {

  // Handy type shortcuts
  String UInt16 = "uint16";
  String UInt24 = "uint24";
  String UInt32 = "uint32";
  String UInt64 = "uint64";
  String UInt256 = "uint256";
  String UInt384 = "uint384";
  String UInt512 = "uint512";
  String Bytes = "bytes";
  String Hash32 = "bytes32";
  String Bool = "bool";
  String Address = "address";
  String String = "string";

  /**
   * Specifies type and size (for fixed sizes). If custom type is specified, it overrides default
   * type mapped from Java class.
   *
   * <p>Type should be one of: "uint", "bytes", "hash", "boolean", "address", "string", "container".
   *
   * <p>Size could be omitted if it's not fixed. Otherwise for non-byte types size should be
   * multiplier of 8 as size is in bits. For byte types ("bytes", "hash", "address", "string") size
   * is provided in bytes. Size is required for "uint" type.
   *
   * <p>Numeric types and other fixed size types doesn't support null values. On attempt to encode
   * such value {@link NullPointerException} will be thrown.
   *
   * <p>For List declare type of values, which list holds, if type of this values could not be
   * mapped automatically.
   *
   * <p>Types and default mapping:
   *
   * <ul>
   *   <li>"uint" - unsigned integer. short.class is mapped to "uint16", int.class is mapped to
   *       "uint32", long.class is mapped to "uint64" by default, BigInteger is mapped to "uint512"
   *   <li>"bytes" - bytes data. byte[].class is mapped to "bytes"
   *   <li>"hash" - same as bytes, but purposed to use to store hash. no types are mapped to "hash"
   *       by default
   *   <li>"address" - bytes with size of 20, standard address size. no types are mapped to
   *       "address" by default
   *   <li>"bool" - bool type. boolean.class is mapped to "bool"
   *   <li>"string" - string, text type. String.class is mapped to "string"
   *   <li>"container" - type designed to store another model inside. Any class which has no default
   *       mapping will be handled as Container and should be SSZ-serializable.
   * </ul>
   *
   * <p>Examples: "bytes", "hash32"
   */
  String type() default "";

  /** Indicates vector type (list with fixed length) and specifies this vector length */
  int vectorLength() default 0;

  /** Indicates list maximum size */
  long maxSize() default 0;

  /**
   * Indicates vector type (list with fixed length) and specifies the external variable name which
   * should be resolved to the vector length at runtime during type hierarchy construction The
   * variable is intended to be resolved via {@link org.ethereum.beacon.ssz.ExternalVarResolver}
   * instance
   */
  String vectorLengthVar() default "";

  /**
   * Indicates list type (list with maximum size) and specifies the external variable name which
   * should be resolved to the maximum size at runtime during type hierarchy construction The
   * variable is intended to be resolved via {@link org.ethereum.beacon.ssz.ExternalVarResolver}
   * instance
   */
  String maxSizeVar() default "";

  /** Defines the order of SSZ property inside a container */
  int order() default -1;
}
