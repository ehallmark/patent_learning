package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.enums.ListenerEvent;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.VectorsListener;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.Word2Vec;
import tools.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;

import tools.Emailer;

import java.io.File;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Created by ehallmark on 8/16/16.
 */
public class BuildParagraphVectors {
    public BuildParagraphVectors(File vocabFile, File vocabFileWithLabels, File vectorFile, DatabaseLabelledIterator iterator, Collection<String> patentCollection) throws Exception {

        VocabCache<VocabWord> vocabCache;

        System.out.println("Checking existence of vocab file...");
        if(vocabFileWithLabels.exists()) {
            vocabCache = WordVectorSerializer.readVocab(vocabFileWithLabels);
        } else {
            if (vocabFile.exists()) {
                vocabCache = WordVectorSerializer.readVocab(vocabFile);
            } else {
                System.out.println("Setting up iterator...");

                AbstractSequenceIterator<VocabWord> sequenceIterator = createSequenceIterator(iterator);

                System.out.println("Starting on vocab building...");


                vocabCache = new AbstractCache.Builder<VocabWord>()
                        .hugeModelExpected(true)
                        .minElementFrequency(Constants.MIN_WORDS_PER_SENTENCE)
                        .build();

                /*
                    Now we should build vocabulary out of sequence iterator.
                    We can skip this phase, and just set AbstractVectors.resetModel(TRUE), and vocabulary will be mastered internally
                */
                VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                        .addSource(sequenceIterator, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                        .setTargetVocabCache(vocabCache)
                        .build();

                constructor.buildJointVocabulary(false, true);

                WordVectorSerializer.writeVocab(vocabCache, vocabFile);
                System.out.println("Vocabulary finished...");
                sequenceIterator.reset();
                new Emailer("Finished vocabulary!");

            }

            // test
            if(!vocabCache.containsWord("6509257")) {
                System.out.println("We dont have patent 6509257 but we should... get labels again");
                // add special labels of each text
                AtomicInteger i = new AtomicInteger(0);
                if(patentCollection==null) {
                    ResultSet rs = Database.selectRawPatentNames();
                    while (rs.next()) {
                        String patent = rs.getString(1).split("_")[0];
                        VocabWord word = new VocabWord(1.0, patent);
                        word.setSequencesCount(1);
                        word.setSpecial(true);
                        word.markAsLabel(true);
                        vocabCache.addToken(word);
                        if (!vocabCache.hasToken(patent)) {
                            word.setIndex(vocabCache.numWords());
                            vocabCache.addWordToIndex(word.getIndex(), patent);
                        }
                        vocabCache.incrementTotalDocCount(1);
                        System.out.println(i.getAndIncrement());
                    }
                } else {
                    patentCollection.forEach(patent->{
                        VocabWord word = new VocabWord(1.0, patent);
                        word.setSequencesCount(1);
                        word.setSpecial(true);
                        word.markAsLabel(true);
                        vocabCache.addToken(word);
                        if (!vocabCache.hasToken(patent)) {
                            word.setIndex(vocabCache.numWords());
                            vocabCache.addWordToIndex(word.getIndex(), patent);
                        }
                        vocabCache.incrementTotalDocCount(1);
                        System.out.println(i.getAndIncrement());
                    });
                }
                System.out.println("Writing vocab...");

                WordVectorSerializer.writeVocab(vocabCache, vocabFileWithLabels);

            }
        }

        System.out.println("Total number of documents: "+vocabCache.totalNumberOfDocs());

        System.out.println("Has patent 6509257: + "+vocabCache.containsWord("6509257"));
        System.out.println("Has word method: + "+vocabCache.containsWord("method")+" count "+vocabCache.wordFrequency("method"));


        StringJoiner toEmail = new StringJoiner("\n");
        toEmail.add("Total number of documents: "+vocabCache.totalNumberOfDocs())
                .add("Has word method: + "+vocabCache.containsWord("method")+" count "+vocabCache.wordFrequency("method"));
        //new Emailer(toEmail.toString());

        int numStopWords = 50;
        Set<String> stopWords = new HashSet<>(vocabCache.vocabWords().stream().sorted((w1, w2)->Double.compare(w2.getElementFrequency(),w1.getElementFrequency())).map(vocabWord->vocabWord.getLabel()).collect(Collectors.toList()).subList(0,numStopWords));

        iterator.setVocabAndStopWords(vocabCache,stopWords);
        SequenceIterator<VocabWord> sequenceIterator = createSequenceIterator(iterator);

        double negativeSampling = 0;

        WeightLookupTable<VocabWord> lookupTable = new InMemoryLookupTable.Builder<VocabWord>()
                .seed(41)
                .negative(negativeSampling)
                .vectorLength(Constants.VECTOR_LENGTH)
                .useAdaGrad(false)
                .cache(vocabCache)
                .build();

         /*
             reset model is viable only if you're setting AbstractVectors.resetModel() to false
             if set to True - it will be called internally
        */
        lookupTable.resetWeights(true);


        // add word vectors

        double sampling = 0;

        System.out.println("Starting paragraph vectors...");
        ParagraphVectors vec = new ParagraphVectors.Builder()
                .seed(41)
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .iterations(3)
                .epochs(5)
                .layerSize(Constants.VECTOR_LENGTH)
                .learningRate(0.025)
                .minLearningRate(0.0001)
                .batchSize(500)
                .windowSize(Constants.MIN_WORDS_PER_SENTENCE)
                .iterate(sequenceIterator)
                .vocabCache(vocabCache)
                .lookupTable(lookupTable)
                .resetModel(false)
                .trainElementsRepresentation(true)
                .trainSequencesRepresentation(true)
                //.elementsLearningAlgorithm(new SkipGram<>())
                //.sequenceLearningAlgorithm(new DBOW())
                .sampling(sampling)
                .setVectorsListeners(Arrays.asList(new VectorsListener<VocabWord>() {
                    @Override
                    public boolean validateEvent(ListenerEvent event, long argument) {
                        if(event.equals(ListenerEvent.LINE)&&argument%200000==0) return true;
                        else if(event.equals(ListenerEvent.EPOCH)) return true;
                        else return false;
                    }

                    @Override
                    public void processEvent(ListenerEvent event, SequenceVectors<VocabWord> sequenceVectors, long argument) {
                        printResults("semiconductor",sequenceVectors);
                        printResults("internet",sequenceVectors);
                        printResults("invention",sequenceVectors);
                        StringJoiner sj = new StringJoiner("\n");
                       /* sj.add("Similarity Report: ")
                                .add(Test.similarityMessage("8142281","7455590",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("9005028","7455590",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("8142843","7455590",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("computer","network",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("wireless","network",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("substrate","network",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("substrate","nucleus",sequenceVectors.getLookupTable()))
                                .add(Test.similarityMessage("substrate","chemistry",sequenceVectors.getLookupTable()));
                        System.out.println(sj.toString());
                        if(event.equals(ListenerEvent.EPOCH)) new Test(sequenceVectors.getLookupTable());*/
                    }
                }))
                .negativeSample(negativeSampling)
                .workers(4)
                .build();

        System.out.println("Starting to train paragraph vectors...");
        vec.fit();

        System.out.println("Finished paragraph vectors...");


        /*
            In training corpus we have few lines that contain pretty close words invloved.
            These sentences should be pretty close to each other in vector space
            line 3721: This is my way .
            line 6348: This is my case .
            line 9836: This is my house .
            line 12493: This is my world .
            line 16393: This is my work .
            this is special sentence, that has nothing common with previous sentences
            line 9853: We now have one .
            Note that docs are indexed from 0
         */

        System.out.println("Writing to file...");
        WordVectorSerializer.writeWordVectors(vec, vectorFile);

        System.out.println("Done...");

        System.out.println("Reading from file...");

        vec = WordVectorSerializer.readParagraphVectorsFromText(vectorFile);

        double sim = vec.similarity("internet", "network");
        System.out.println("internet/computer similarity: " + sim);

        double sim2 = vec.similarity("internet", "protein");
        System.out.println("internet/protein similarity (should be lower): " + sim2);


        new Test(vec.lookupTable());


    }


    /*public static void createDataFolder(SentencePreProcessor preProcessor) throws Exception {

        BasePatentIterator iter = new BasePatentIterator(Constants.START_DATE);
        iter.reset();
        while(iter.hasNext()) {
            String nextSentence = iter.nextSentence();
            String label = iter.currentLabel();

            List<String> toAdd = Arrays.asList(preProcessor.preProcess(nextSentence).split("\\s+")).stream().filter(s->s!=null&&s.length()>0).collect(Collectors.toList());
            Database.insertRawPatent(label, toAdd);
        }

    }*/

    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();
        Database.setupInsertConn();
        new BuildParagraphVectors(new File(Constants.VOCAB_FILE), new File(Constants.VOCAB_FILE_WITH_LABELS), new File(Constants.WORD_VECTORS_PATH), new DatabaseLabelledIterator(), null);
        /*try {
            new BuildParagraphVectors(new File("compdb_vocab_file.txt"), new File("compdb_vocab_with_labels.txt"), new File("compdb_paragraph_vectors.txt"), new EtsiLabelledIterator(), Constants.ETSI_PATENT_LIST);
        } finally {
            Database.close();
        }*/

    }

    public static void printResults(String word, SequenceVectors<VocabWord> sequenceVectors) {
        System.out.println("Words nearest to "+word+": "+String.join(", ",sequenceVectors.wordsNearest(word,10).stream().collect(Collectors.toList())));
    }

    public static AbstractSequenceIterator<VocabWord> createSequenceIterator(DatabaseLabelledIterator iterator) {
        System.out.println("Iterator transformation...");

        MySentenceTransformer transformer = new MySentenceTransformer.Builder().iterator(iterator).build();

        System.out.println("Building sequence iterator from transformer...");

        return new AbstractSequenceIterator.Builder<>(transformer).build();
    }
}
