package tech.pegasys.teku.spec.executionengine.client.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class NewBlockResponse {

  private Boolean valid;

  public NewBlockResponse(@JsonProperty("valid") Boolean valid) {
    this.valid = valid;
  }

  public Boolean getValid() {
    return valid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NewBlockResponse that = (NewBlockResponse) o;
    return Objects.equals(valid, that.valid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valid);
  }

  @Override
  public String toString() {
    return "NewBlockResponse{" + "valid=" + valid + '}';
  }
}
