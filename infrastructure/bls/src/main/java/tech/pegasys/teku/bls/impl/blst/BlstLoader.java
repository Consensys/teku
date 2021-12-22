/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.blst;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;

/**
 * The BLST JNI classes use a static block to automatically load the native library. That means that
 * it loads as soon as the class is loaded which is hard to control. Since the native library may
 * not be supported on all platforms we need to control when the native library loads and handle any
 * exceptions from it by using a fallback instead.
 *
 * <p>So this class only refers to Blst classes via reflection to ensure just referencing this class
 * does not trigger those classes to load.
 */
public class BlstLoader {
  private static final Logger LOG = LogManager.getLogger();

  private static final String LIBRARY_NAME = System.mapLibraryName("blst");
  private static final String OS_NAME = System.getProperty("os.name").replaceFirst(" .*", "");

  public static Optional<BLS12381> INSTANCE = loadBlst();

  private static Optional<BLS12381> loadBlst() {
    try {
      if (optimisedLibraryIsSupported()) {
        StatusLogger.STATUS_LOG.reportOptimisedBlst();
        useOptimisedBlstLibrary();
      } else {
        StatusLogger.STATUS_LOG.warnPortableBlst();
      }
      // Trigger loading of native library
      Class.forName("supranational.blst.blstJNI");

      // Load actual implementation - *might* trigger loading but doesn't always
      final Class<?> blstClass = Class.forName("tech.pegasys.teku.bls.impl.blst.BlstBLS12381");
      return Optional.of((BLS12381) blstClass.getDeclaredConstructor().newInstance());
    } catch (final InstantiationException
        | ExceptionInInitializerError
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException
        | ClassNotFoundException e) {
      LOG.error("Couldn't load native BLS library", e);
      return Optional.empty();
    }
  }

  private static boolean optimisedLibraryIsSupported() {
    final String forcedPortableBlst = System.getProperty("teku.portableBlst");
    if (forcedPortableBlst != null) {
      LOG.info("BLST portable version was explicitly set to {}", forcedPortableBlst);
      return !Boolean.parseBoolean(forcedPortableBlst);
    }
    try {
      switch (OS_NAME) {
        case "Linux":
          return LinuxCpuInfo.supportsOptimisedBlst();
        case "Mac":
          return MacCpuInfo.supportsOptimisedBlst();
        default:
          return false;
      }
    } catch (final Throwable t) {
      LOG.warn("Unable to check if optimised BLST is supported", t);
      return false;
    }
  }

  private static void useOptimisedBlstLibrary() {
    final String optimisedResource =
        OS_NAME + "/optimised" + "/" + System.getProperty("os.arch") + "/" + LIBRARY_NAME;
    System.setProperty("supranational.blst.jniResource", optimisedResource);
  }
}
