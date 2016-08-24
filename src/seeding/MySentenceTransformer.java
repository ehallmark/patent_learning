package seeding;

/**
 * Created by ehallmark on 8/22/16.
 */

import lombok.NonNull;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.transformers.SequenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.documentiterator.BasicLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.DocumentIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This simple class is responsible for conversion lines of text to Sequences of SequenceElements to fit them into SequenceVectors model
 *
 * @author raver119@gmail.com
 */
public class MySentenceTransformer implements SequenceTransformer<VocabWord, String>, Iterable<Sequence<VocabWord>>{
    /*
            So, we must accept any SentenceIterator implementations, and build vocab out of it, and use it for further transforms between text and Sequences
     */
    protected TokenizerFactory tokenizerFactory;
    protected DatabaseLabelledIterator iterator;
    protected boolean readOnly = false;
    protected AtomicInteger sentenceCounter = new AtomicInteger(0);

    private MySentenceTransformer(@NonNull DatabaseLabelledIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public Sequence<VocabWord> transformToSequence(String object) {
        throw new UnsupportedOperationException("TRYING TO CONVERT STRING INTO SEQUENCE BUT SEQUENCES ARE PREBUILT!");
    }

    public Sequence<VocabWord> transformToSequence(List<VocabWord> words, String label) {
        Sequence<VocabWord> sequence = new Sequence<>();

        for(VocabWord word : words) {
            sequence.addElement(word);
        }
        sequence.setSequenceId(sentenceCounter.getAndIncrement());
        VocabWord vLabel = new VocabWord(1.0, label);
        vLabel.markAsLabel(true);
        vLabel.setSequencesCount(1);
        vLabel.setSpecial(true);

        sequence.setSequenceLabel(vLabel);
        sequence.setSequenceLabels(Arrays.asList(vLabel));
        return sequence;
    }

    @Override
    public Iterator<Sequence<VocabWord>> iterator() {
        iterator.reset();

        return new Iterator<Sequence<VocabWord>>() {
            @Override
            public synchronized boolean hasNext() {
                return MySentenceTransformer.this.iterator.hasNextDocument();
            }

            @Override
            public synchronized Sequence<VocabWord> next() {
                LabelledDocument document = iterator.nextDocument();
                if  (document.getReferencedContent() == null) throw new RuntimeException("RETURNING AN EMPTY SEQUENCE: "+document.getLabel());
                assert(document.getLabel()!=null) : "DOCUMENT HAS NO LABEL!!!!";
                Sequence<VocabWord> sequence = MySentenceTransformer.this.transformToSequence(document.getReferencedContent(),document.getLabel());
                return sequence;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static class Builder {
        protected TokenizerFactory tokenizerFactory;
        protected DatabaseLabelledIterator iterator;
        protected boolean readOnly = false;

        public Builder() {

        }

        public MySentenceTransformer.Builder tokenizerFactory(@NonNull TokenizerFactory tokenizerFactory) {
            this.tokenizerFactory = tokenizerFactory;
            return this;
        }

        public MySentenceTransformer.Builder iterator(@NonNull DatabaseLabelledIterator iterator) {
            this.iterator = iterator;
            return this;
        }


        public MySentenceTransformer.Builder readOnly(boolean readOnly) {
            this.readOnly = true;
            return this;
        }

        public MySentenceTransformer build() {
            MySentenceTransformer transformer = new MySentenceTransformer(this.iterator);
            transformer.tokenizerFactory = this.tokenizerFactory;
            transformer.readOnly = this.readOnly;
            return transformer;
        }
    }
}
