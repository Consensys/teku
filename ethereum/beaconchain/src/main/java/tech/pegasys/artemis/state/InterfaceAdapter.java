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

package tech.pegasys.artemis.state;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class InterfaceAdapter<T> implements JsonSerializer<T>, JsonDeserializer<T> {

  @Override
  public JsonElement serialize(T object, Type typeOfT, JsonSerializationContext context) {
    JsonObject objectWrapper = new JsonObject();
    objectWrapper.add("data", new Gson().toJsonTree(object));
    objectWrapper.addProperty("type", object.getClass().getName());
    return objectWrapper;
  }

  @Override
  public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject objectWrapper = (JsonObject) json;
    JsonElement data = objectWrapper.get("data");
    JsonElement type = objectWrapper.get("type");
    if (data == null || type == null) {
      throw new JsonParseException("No member found in interface wrapper");
    }
    try {
      Type realType = Class.forName(type.getAsString());
      return context.deserialize(data, realType);
    } catch (ClassNotFoundException e) {
      throw new JsonParseException(e);
    }
  }
}
