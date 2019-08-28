package pegasys.artemis.reference;

import java.nio.file.Path;

public class TestObject {
  String fileName;
  Class className;
  Path path;

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public Class getClassName() {
    return className;
  }

  public void setClassName(Class className) {
    this.className = className;
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  public TestObject(String fileName, Class className, Path path) {
    this.fileName = fileName;
    this.className = className;
    this.path = path;
  }
}
