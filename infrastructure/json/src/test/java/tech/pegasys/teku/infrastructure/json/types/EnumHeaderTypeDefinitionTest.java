package tech.pegasys.teku.infrastructure.json.types;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumHeaderTypeDefinitionTest {
    final DeserializableTypeDefinition<Color> definition = DeserializableTypeDefinition.enumOf(Color.class);

    enum Color {
        RED, GREEN, BLUE
    }

    @Test
    void shouldSerializeEnum() throws Exception {
        final String json = JsonUtil.serialize(Color.RED, definition);
        assertThat(json).isEqualTo("\"RED\"");
    }

    @Test
    void shouldParseEnum() throws Exception {
        assertThat(JsonUtil.parse("\"BLUE\"", definition)).isEqualTo(Color.BLUE);
    }

    @Test
    public void serializeOpenApiType() throws IOException {

        final EnumHeaderTypeDefinition<Color> definition = new EnumHeaderTypeDefinition<>(
                Color.class,
                Color::name,
                Optional.of("color"),
                "Color",
                Optional.of(true),
                Optional.of("The color of the object"),
                Optional.of("RED")
        );

        final StringWriter writer = new StringWriter();
        final JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
        gen.writeStartObject();
        definition.serializeOpenApiType(gen);
        gen.writeEndObject();
        gen.close();
        final String json = writer.toString();

        final String expectedJson = "{\"Color\":{\"description\":\"The color of the object\",\"required\":true,\"schema\":{\"type\":\"string\",\"enum\":[\"RED\",\"GREEN\",\"BLUE\"],\"example\":\"RED\"}}}";
        assertEquals(expectedJson, json);
    }
}