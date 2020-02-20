package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class KdfParamsJsonDeserializer extends StdDeserializer<KdfParams> {
    public KdfParamsJsonDeserializer() {
        this(null);
    }

    public KdfParamsJsonDeserializer(final Class<?> vc) {
        super(vc);
    }

    @Override
    public KdfParams deserialize(final JsonParser parser, final DeserializationContext ctxt) throws IOException {
        TreeNode node = parser.readValueAsTree();
        if (hasSCryptAttributes(node)) {
            return parser.getCodec().treeToValue(node, SCryptParams.class);
        }

        return parser.getCodec().treeToValue(node, Pbkdf2Params.class);
    }

    private boolean hasSCryptAttributes(final TreeNode node) {
        return node.get("n") != null && node.get("p") != null && node.get("r") != null;
    }
}
