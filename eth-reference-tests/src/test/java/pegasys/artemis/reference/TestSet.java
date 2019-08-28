package pegasys.artemis.reference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestSet extends ArrayList<TestObject> {

  public Path path;

  public TestSet(Path path) {
    this.path = path;
  }

  //provides a list of fileNames without duplicates
  public List<String> getFileNames(){
    return this.stream().map(TestObject::getFileName).distinct().collect(Collectors.toList());
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  public List<TestObject> getTestObjectByFileName(String fileName) {
    return this.stream().filter(testObject -> testObject.getFileName().equals(fileName)).collect(Collectors.toList());
  }
}
