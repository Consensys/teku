/*
 * Copyright 2020 ConsenSys AG.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;

/**
 * A simple library class which helps with loading dynamic libraries stored in the JAR archive.
 * These libraries usually contain implementation of some methods in native code (using JNI - Java
 * Native Interface).
 *
 * @see <a
 *     href="http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar">http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar</a>
 * @see <a
 *     href="https://github.com/adamheinrich/native-utils">https://github.com/adamheinrich/native-utils</a>
 */
public class NativeUtils {

  /**
   * The minimum length a prefix for a file has to have according to {@link
   * File#createTempFile(String, String)}}.
   */
  private static final int MIN_PREFIX_LENGTH = 3;

  public static final String NATIVE_FOLDER_PATH_PREFIX = "nativeutils";

  /** Temporary directory which will contain the DLLs. */
  private static File temporaryDir;

  /** Private constructor - this class will never be instanced */
  private NativeUtils() {}

  /**
   * Loads library from current JAR archive
   *
   * <p>The file from JAR is copied into system temporary directory and then loaded. The temporary
   * file is deleted after exiting. Method uses String as filename because the pathname is
   * "abstract", not system-dependent.
   *
   * @param path The path of file inside JAR as absolute path (beginning with '/'), e.g.
   *     /package/File.ext
   * @throws IOException If temporary file creation or read/write operation fails
   * @throws IllegalArgumentException If source file (param path) does not exist
   * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than
   *     three characters (restriction of {@link File#createTempFile(java.lang.String,
   *     java.lang.String)}).
   * @throws FileNotFoundException If the file could not be found inside the JAR.
   */
  public static void loadLibraryFromJar(String path) throws IOException {

    if (null == path || !path.startsWith("/")) {
      throw new IllegalArgumentException("The path has to be absolute (start with '/').");
    }

    // Obtain filename from path
    String[] parts = path.split("/", -1);
    String filename = (parts.length > 1) ? parts[parts.length - 1] : null;

    // Check if the filename is okay
    if (filename == null || filename.length() < MIN_PREFIX_LENGTH) {
      throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
    }

    // Prepare temporary file
    if (temporaryDir == null) {
      temporaryDir = createTempDirectory(NATIVE_FOLDER_PATH_PREFIX);
      temporaryDir.deleteOnExit();
    }

    File temp = new File(temporaryDir, filename);

    try (InputStream is = NativeUtils.class.getResourceAsStream(path)) {
      Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      temp.delete();
      throw e;
    } catch (NullPointerException e) {
      temp.delete();
      throw new FileNotFoundException("File " + path + " was not found inside JAR.");
    }

    try {
      System.load(temp.getAbsolutePath());
    } finally {
      if (isPosixCompliant()) {
        // Assume POSIX compliant file system, can be deleted after loading
        temp.delete();
      } else {
        // Assume non-POSIX, and don't delete until last file descriptor closed
        temp.deleteOnExit();
      }
    }
  }

  private static boolean isPosixCompliant() {
    try {
      return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    } catch (FileSystemNotFoundException | ProviderNotFoundException | SecurityException e) {
      return false;
    }
  }

  private static File createTempDirectory(String prefix) throws IOException {
    String tempDir = System.getProperty("java.io.tmpdir");
    File generatedDir = new File(tempDir, prefix + System.nanoTime());

    if (!generatedDir.mkdir())
      throw new IOException("Failed to create temp directory " + generatedDir.getName());

    return generatedDir;
  }
}
