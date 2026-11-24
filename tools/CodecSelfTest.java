import com.veniilocal.VeniiFrameCodec;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/** Run locally without Android SDK: javac + java. */
public final class CodecSelfTest {
    public static void main(String[] args) throws Exception {
        String[] inputs = {
            "{\"id\":67,\"content\":\"test-token\"}",
            "[{\"from\":\"android\",\"to_name\":\"aa:bb:cc\",\"from_name\":\"local\",\"type\":\"cmd\",\"id\":888}]",
            "[{\"from\":\"android\",\"to_name\":\"aa:bb:cc\",\"from_name\":\"local\",\"type\":\"cmd\",\"id\":1020}]",
            "{\"texte\":\"éàè العربية\"}"
        };
        for (String input : inputs) {
            byte[] pkt = VeniiFrameCodec.encode(input);
            if (!input.equals(VeniiFrameCodec.decodeFrame(new ByteArrayInputStream(pkt))))
                throw new AssertionError("Round-trip échoué");
            if (!Arrays.equals(pkt, VeniiFrameCodec.encode(input)))
                throw new AssertionError("Chiffrement non déterministe");
            System.out.println("OK : " + pkt.length + " octets");
        }
        System.out.println("TESTS AES + Base64 + TCP : réussis hors ligne.");
    }
}
