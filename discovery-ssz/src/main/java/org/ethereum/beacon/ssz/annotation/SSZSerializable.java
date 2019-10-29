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
import org.ethereum.beacon.ssz.access.SSZBasicAccessor;
import org.ethereum.beacon.ssz.access.SSZContainerAccessor;
import org.ethereum.beacon.ssz.access.SSZListAccessor;

/**
 * Identifies class that is SSZ serializable
 *
 * <p>Required to mark SSZ compatible class
 */
@Documented
@Target(ElementType.TYPE)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface SSZSerializable {

  interface Void {}

  interface VoidListAccessor extends Void, SSZListAccessor {}

  interface VoidContainerAccessor extends Void, SSZContainerAccessor {}

  interface VoidBasicAccessor extends Void, SSZBasicAccessor {}

  /**
   * Tells the Serializer that this class should be serialized as <code>serializeAs</code> class
   *
   * <p>- This class should be an ancestor of the <code>serializeAs</code> class - This class should
   * have a public constructor that takes single <code>serializeAs</code> class instance as an
   * argument
   */
  Class<?> serializeAs() default void.class;

  /**
   * Call this method to get target serializable instance. This is handy for wrapper classes which
   * delegate all calls to a wrapped instance which is serializable
   */
  String instanceGetter() default "";

  /** Specifies custom basic value accessor for SSZ serializable class */
  Class<? extends SSZBasicAccessor> basicAccessor() default VoidBasicAccessor.class;

  /** Specifies custom list accessor for SSZ serializable class */
  Class<? extends SSZListAccessor> listAccessor() default VoidListAccessor.class;

  /** Specifies custom container accessor for SSZ serializable class */
  Class<? extends SSZContainerAccessor> containerAccessor() default VoidContainerAccessor.class;

  /**
   * Applicable to SSZ Containers with a single child only When set to true the wrapping container
   * is not serialized, and its child is serialized the same way as if it was serialized directly
   */
  boolean skipContainer() default false;
}
