package seeding;

import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;

/**
 * Created by ehallmark on 6/28/16.
 */
public class MyPreprocessor implements TokenPreProcess {
    @Override
    public String preProcess(String token) {
        return token.toLowerCase().replaceAll("[-_\\s\\s+]"," ").replaceAll("[^a-z ]", "");
    }
}
