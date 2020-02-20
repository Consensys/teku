package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class KdfParamsJsonSerializer extends StdSerializer<KdfParams> {

    public KdfParamsJsonSerializer(final Class<KdfParams> t) {
        super(t);
    }

    @Override
    public void serialize(final KdfParams kdfParams, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("dklen", kdfParams.getDklen());

        if (kdfParams instanceof SCryptParams) {
            final SCryptParams sCryptParams = (SCryptParams) kdfParams;
            gen.writeNumberField("n", sCryptParams.getN());
            gen.writeNumberField("p", sCryptParams.getP());
            gen.writeNumberField("r", sCryptParams.getR());
        } else if (kdfParams instanceof Pbkdf2Params) {
            final Pbkdf2Params pbkdf2Params = (Pbkdf2Params) kdfParams;
            gen.writeNumberField("c", pbkdf2Params.getC());
            gen.writeStringField("prf", pbkdf2Params.getPrf());
        }

        gen.writeStringField("salt", kdfParams.getSalt());
        gen.writeEndObject();
    }


}
