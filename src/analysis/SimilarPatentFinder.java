package analysis;

import ca.pjer.ekmeans.EKmeans;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicDouble;
import org.apache.xpath.operations.Number;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.eclipse.jetty.util.ArrayQueue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.*;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.*;
import tools.*;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import server.tools.AbstractPatent;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    protected MinHeap<Patent> heap;
    protected List<Patent> patentList;
    protected String name;
    private static TokenizerFactory tf = new DefaultTokenizerFactory();
    private static Map<String,INDArray> globalCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,List<String>> patentWordsCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,INDArray> globalCandidateAvgCache = Collections.synchronizedMap(new HashMap<>());
    static {
        tf.setTokenPreProcessor(new MyPreprocessor());
    }

    public static Map<String,INDArray> getGlobalCache() {
        return globalCache;
    }

    //private Map<String, String> assigneeMap;

    public SimilarPatentFinder(Map<String,Pair<Float,INDArray>> vocab) throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE), "**ALL**", vocab);
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, Map<String,Pair<Float,INDArray>> vocab) throws SQLException,IOException,ClassNotFoundException {
        this(candidateSet,patentListFile,name,null, vocab);
    }

    public SimilarPatentFinder(String name,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        this(name, getVectorFromDB(name, vocab));
    }

    public SimilarPatentFinder(String name, INDArray data) throws SQLException {
        this.name=name;
        patentList = data==null?null:Arrays.asList(new Patent(name, data));
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, INDArray eigenVectors, Map<String,Pair<Float,INDArray>> vocab) throws SQLException,IOException, ClassNotFoundException {
        // construct lists
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        if (!patentListFile.exists()) {
            try {
                if (candidateSet == null) {
                    candidateSet = Database.getValuablePatentsToList();
                }
                int arrayCapacity = candidateSet.size();
                patentList = new ArrayList<>(arrayCapacity);
                // go thru candidate set and remove all that we can find
                List<String> toRemove = new ArrayList<>();
                for(String patent : candidateSet) {
                    if(globalCache.containsKey(patent)) {
                        patentList.add(new Patent(patent,globalCache.get(patent)));
                        toRemove.add(patent);
                        System.out.println("Found: "+patent);
                    }
                }
                candidateSet.removeAll(toRemove);
                if(!candidateSet.isEmpty()) {
                    ResultSet rs;
                    rs = Database.selectPatentVectors(candidateSet);
                    int count = 0;
                    int offset = 2; // Due to the pub_doc_number field
                    while (rs.next()) {
                        try {
                            INDArray array = handleResultSet(rs, offset, vocab);
                            if (array != null) {
                                patentList.add(new Patent(rs.getString(1), array));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        System.out.println(++count);
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            // Serialize List
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
            oos.writeObject(patentList);
            oos.flush();
            oos.close();
        } else {
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            Object obj = ois.readObject();
            patentList = ((List<Patent>) obj);
            // PCA
            if(eigenVectors!=null) patentList.forEach(p->p.getVector().mmuli(eigenVectors));
            ois.close();
        }

        // Now add stuff to the map
        patentList.forEach(p->{
            globalCache.put(p.getName(),p.getVector());
        });

        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(int limit, Map<String,Pair<Float,INDArray>> vocab, String patent) throws SQLException {
        return predictKeywords(limit, vocab, patent, null);
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(int limit, Map<String,Pair<Float,INDArray>> vocab, String patent, INDArray docVector) throws SQLException {
        if(patentWordsCache.containsKey(patent)) return predictKeywords(patentWordsCache.get(patent),limit,vocab,docVector);
        // otherwise, if not cached
        ResultSet rs = Database.getBaseVectorFor(patent);
        if(rs.next()) {
            List<String> tokens = new ArrayList<>();
            tokens.addAll(tf.create(rs.getString(1)).getTokens());
            tokens.addAll(tf.create(rs.getString(2)).getTokens());
            tokens.addAll(tf.create(rs.getString(3)).getTokens());
            patentWordsCache.put(patent, tokens);
            return predictKeywords(tokens,limit,vocab, docVector);
        } else {
            return null;
        }
    }

    private static List<WordFrequencyPair<String,Float>> mergeKeywordPredictions(int limit, Map<String,List<WordFrequencyPair<String,Float>>> cache, Collection<String> patents) throws SQLException {
        Map<String,Float> wordFreqMap = new HashMap<>();
        for(String patent : patents) {
            System.out.println("    Predicting "+patent);
            cache.get(patent).forEach(prediction->{
                if(wordFreqMap.containsKey(prediction.getFirst())) {
                    wordFreqMap.put(prediction.getFirst(), wordFreqMap.get(prediction.getFirst())+prediction.getSecond());
                } else {
                    wordFreqMap.put(prediction.getFirst(), prediction.getSecond());
                }
            });
        }
        return wordFreqMap.entrySet().stream().map(e->new WordFrequencyPair<>(e.getKey(),e.getValue())).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(limit).collect(Collectors.toList());
    }

    public List<Map.Entry<String,Pair<Double,Set<String>>>> autoClassify(Map<String,Pair<Float,INDArray>> vocab, int k, int numPredictions, boolean equal, int iterations) throws Exception {
        // k-means
        int numData = patentList.size();
        final int numClusters = k;
        double[][] points = new double[numData][Constants.VECTOR_LENGTH];
        for (int i = 0; i < numData; i++) {
            Patent p = patentList.get(i);
            points[i] = Transforms.unitVec(p.getVector()).data().asDouble();
        }
        double[][] centroids = new double[numClusters][Constants.VECTOR_LENGTH];
        org.nd4j.linalg.api.rng.Random rand = new DefaultRandom(41);
        for (int i = 0; i < numClusters; i++) {
            centroids[i] = Nd4j.rand(1,Constants.VECTOR_LENGTH, -1.0d, 1.0d, rand).data().asDouble();
        }

        EKmeans eKmeans;
        try {
            eKmeans = new EKmeans(centroids, points);
            eKmeans.setEqual(equal);
            eKmeans.setIteration(iterations);
            //eKmeans.setDistanceFunction((d1,d2)->1.0d-Transforms.cosineSim(Nd4j.create(d1),Nd4j.create(d2)));
            eKmeans.run();

        } catch(Exception e) {
            throw new RuntimeException("K means is null :(");
        }

        int[] assignments = eKmeans.getAssignments();
        assert assignments.length==numData : "K means has wrong number of data points!";

        Map<Integer,List<Patent>> kMeansMap = new HashMap<>();
        for(int i = 0; i < numClusters; i++) {
            kMeansMap.put(i, new ArrayList<>());
        }
        for(int i = 0; i < numData; i++) {
            kMeansMap.get(assignments[i]).add(patentList.get(i));
        }

        // compute centroids
        Map<Integer,INDArray> centroidMap = new HashMap<>();
        kMeansMap.entrySet().forEach(e->{
            if(e.getValue().isEmpty())return;
            centroidMap.put(e.getKey(),computeAvg(e.getValue(),null));
        });

        Map<String,List<WordFrequencyPair<String,Float>>> cache = new HashMap<>();
        final int consideredPerElement = 50;
        patentList.forEach(p->{
            System.out.println(p.getName());
            try {
                cache.put(p.getName(),predictKeywords(consideredPerElement,vocab,p.getName(),centroidMap.get(assignments[patentList.indexOf(p)])));
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
        return kMeansMap.entrySet().stream().map(e->{
            try {
                if(e.getValue().isEmpty())return null;
                List<WordFrequencyPair<String,Float>> predictions = mergeKeywordPredictions(numPredictions,cache,e.getValue().stream().map(p->p.getName()).collect(Collectors.toList()));
                if(predictions==null||predictions.isEmpty()) return null;
                return Maps.immutableEntry(String.join("; ",predictions.stream().map(p->p.getFirst()).collect(Collectors.toSet())),new Pair<>(predictions.stream().collect(Collectors.averagingDouble(p->p.getSecond())),e.getValue().stream().map(p->p.getName()).collect(Collectors.toSet())));
            } catch(Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }).filter(p->p!=null).sorted((e2,e1)->e1.getValue().getFirst().compareTo(e2.getValue().getFirst())).collect(Collectors.toList());
    }

    private static void processNGrams(List<String> cleanToks, INDArray docVector, Map<String,AtomicDouble> nGramCounts, Map<String,Pair<Set<String>,AtomicDouble>> stemmedCounts, Map<String,Pair<Float,INDArray>> vocab, int n) {
        assert n >= 1 : "Cannot process n grams if n < 1!";
        Stemmer stemMe = new Stemmer();
        Map<String,Set<String>> newToks = new HashMap<>();
        Set<String> permutationsSet = new HashSet<>();
        for(int i = 0; i < cleanToks.size()-n; i++) {
            List<String> sub = cleanToks.subList(i,i+n);
            List<String> stemSub = sub.stream().map(s->stemMe.stem(s)).collect(Collectors.toList());
            if(stemSub.size()!=sub.size()) {
                continue;
            }

            INDArray toAvg = Nd4j.create(sub.size(), Constants.VECTOR_LENGTH);
            AtomicDouble freq = new AtomicDouble(0.0);
            for(int j = 1; j <= n; j++) {
                try {
                    Pair<Float, INDArray> word = vocab.get(sub.get(j - 1));
                    freq.getAndAdd(word.getFirst());
                    toAvg.putRow(j - 1, word.getSecond());
                    INDArray mean = Nd4j.create(j, Constants.VECTOR_LENGTH);
                    for (int m = 0; m < j; m++) {
                        mean.putRow(m, toAvg.getRow(m));
                    }
                    double weight = Math.pow(j, 1.5) * Transforms.cosineSim(docVector, mean.mean(0)) * freq.get();

                    String next = String.join(" ", sub.subList(0, j));
                    String stemmedNext = String.join(" ", stemSub.subList(0, j));
                    SortedSet<String> permutedStems = new Permutations<String>().permute(stemmedNext.split(" ")).stream().map(perm -> String.join(" ", perm)).collect(Collectors.toCollection(() -> new TreeSet<>()));

                    if (permutedStems.size() > 0) permutationsSet.add(String.join(",", permutedStems));
                    if (newToks.containsKey(stemmedNext)) {
                        newToks.get(stemmedNext).add(next);
                    } else {
                        Set<String> hash = new HashSet<>();
                        hash.add(next);
                        newToks.put(stemmedNext, hash);
                    }
                    if (stemmedCounts.containsKey(stemmedNext)) {
                        Pair<Set<String>, AtomicDouble> p = stemmedCounts.get(stemmedNext);
                        p.getSecond().getAndAdd(weight);
                        p.getFirst().add(next);
                    } else {
                        stemmedCounts.put(stemmedNext, new Pair<>(newToks.get(stemmedNext), new AtomicDouble(weight)));
                    }

                    if (nGramCounts.containsKey(next)) {
                        nGramCounts.get(next).getAndAdd(weight);
                    } else {
                        nGramCounts.put(next, new AtomicDouble(weight));
                    }
                } catch(Exception e) {
                    new Emailer("Exception thrown\ni="+i+" j="+j"+\n"+e.toString());
                    throw new RuntimeException(e.toString());
                }
            }

        }
        for(Map.Entry<String,Pair<Set<String>,AtomicDouble>> e : stemmedCounts.entrySet()) {
            for (Map.Entry<String, Set<String>> newTok : newToks.entrySet()) {
                double stemValue = stemmedCounts.get(newTok.getKey()).getSecond().get();
                if (e.getKey().contains(newTok.getKey()) && e.getKey().length() > newTok.getKey().length()) {
                    if (e.getValue().getSecond().get() >= stemValue) {
                        for (String toRemove : newTok.getValue()) {
                            if (nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
                        }
                    } else {
                        for(String toRemove : e.getValue().getFirst()) {
                            if(nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
                        }
                    }
                }
            }
        }

        for(Map.Entry<String,Set<String>> newTok : newToks.entrySet()) {
            double stemValue = stemmedCounts.get(newTok.getKey()).getSecond().get();
            SortedSet<WordFrequencyPair<String,Double>> data = newTok.getValue().stream().filter(s -> nGramCounts.containsKey(s)).map(s -> new WordFrequencyPair<>(s, nGramCounts.get(s).get())).collect(Collectors.toCollection(()->new TreeSet<>()));
            for(WordFrequencyPair<String,Double> pair : data) {
                if(pair.equals(data.last()))continue;
                String toRemove = pair.getFirst();
                if (nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
            }
            if(!data.isEmpty())nGramCounts.get(data.last()).set(stemValue);
        }

        for (String tok : permutationsSet) {
            if (tok == null || tok.split(",") == null || tok.split(",").length == 0) continue;
            SortedSet<WordFrequencyPair<String, Double>> data = new TreeSet<>();
            for (String permStem : Arrays.asList(tok.split(","))) {
                if (permStem == null || permStem.length() == 0) continue;
                if (newToks.containsKey(permStem)) {
                    for (String ngram : newToks.get(permStem)) {
                        if (ngram == null) continue;
                        if (nGramCounts.containsKey(ngram)) {
                            data.add(new WordFrequencyPair<>(ngram, nGramCounts.get(ngram).get()));
                        }
                    }
                }
            }
            for(WordFrequencyPair<String,Double> pair : data) {
                if(pair.equals(data.last()))continue;
                String toRemove = pair.getFirst();
                if (nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
            }
            if (!data.isEmpty())
                nGramCounts.get(data.last().getFirst()).set(data.stream().collect(Collectors.summingDouble(d -> d.getSecond())));
        }

    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(String text, int limit, Map<String,Pair<Float,INDArray>> vocab) {
        return predictKeywords(tf.create(text).getTokens(),limit,vocab);
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(List<String> tokens, int limit, Map<String,Pair<Float,INDArray>> vocab) {
        return predictKeywords(tokens,limit,vocab,null);
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(List<String> tokens, int limit, Map<String,Pair<Float,INDArray>> vocab, INDArray docVector) {
        Map<String,AtomicDouble> nGramCounts = new HashMap<>();
        Map<String,Pair<Set<String>,AtomicDouble>> stemmedCounts = new HashMap<>();
        tokens = tokens.stream().map(s->s!=null&&s.trim().length()>0&&!Constants.STOP_WORD_SET.contains(s)&&vocab.containsKey(s)?s:null).filter(s->s!=null).collect(Collectors.toList());
        if(docVector==null) docVector= VectorHelper.TFIDFcentroidVector(vocab,tokens);
        final int n = 3;
        try {
            processNGrams(tokens, docVector, nGramCounts, stemmedCounts, vocab, n);
        } catch(Exception e) {
            new Emailer("Exception processing n grams!\n"+e.toString());
        }

        MinHeap<WordFrequencyPair<String,Float>> heap = MinHeap.setupWordFrequencyHeap(limit);
        nGramCounts.entrySet().stream().map(e->{
            WordFrequencyPair<String,Float> newPair = new WordFrequencyPair<>(e.getKey(),(float)e.getValue().get());
            return newPair;
        }).forEach(s->{
            heap.add(s);
        });

        List<WordFrequencyPair<String,Float>> results = new ArrayList<>(limit);
        while(!heap.isEmpty()) {
            WordFrequencyPair<String,Float> pair = heap.remove();
            results.add(0, pair);
        }

        return results;
    }

    public List<Patent> getPatentList() {
        return patentList;
    }

    public static INDArray handleResultSet(ResultSet rs, int offset, Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        List<String> tokens = new ArrayList<>();
        String description = rs.getString(offset);
        RecursiveTask<List<String>> descTask = new GetTokensThread(tf,description);
        descTask.fork();
        String abstractText = rs.getString(offset+1);
        RecursiveTask<List<String>> absTask = new GetTokensThread(tf,abstractText);
        absTask.fork();
        String claims = rs.getString(offset+2);
        RecursiveTask<List<String>> claimTask = new GetTokensThread(tf,claims);
        claimTask.fork();
        tokens.addAll(descTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        tokens.addAll(absTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        tokens.addAll(claimTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        return VectorHelper.TFIDFcentroidVector(vocab,tokens);
    }

    public String getName() {
        return name;
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public static INDArray computeAvg(List<Patent> patentList, String candidateSetName) {
        if(candidateSetName!=null&&globalCandidateAvgCache.containsKey(candidateSetName)) {
            return globalCandidateAvgCache.get(candidateSetName);
        }
        INDArray thisAvg = Nd4j.create(patentList.size(),Constants.VECTOR_LENGTH);
        for(int i = 0; i < patentList.size(); i++) {
            thisAvg.putRow(i, patentList.get(i).getVector());
        }
        INDArray avg = thisAvg.mean(0);
        if(candidateSetName!=null) {
            globalCandidateAvgCache.put(candidateSetName,avg);
        }
        return avg;
    }

    public List<PatentList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, boolean findDissimilar) throws SQLException {
        List<PatentList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            try {
                list.addAll(similarFromCandidateSet(other, threshold, limit, findDissimilar));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        });
        return list;
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, boolean findDissimilar) throws SQLException {
        // Find the highest (pairwise) assets
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return new ArrayList<>();
        List<PatentList> lists = new ArrayList<>();
        INDArray otherAvg = computeAvg(other.patentList,other.getName());
        Set<String> dontMatch = other.name.equals(this.name) ? null : other.patentList.stream().map(p->p.getName()).collect(Collectors.toSet());
        try {
            if(findDissimilar) lists.addAll(findOppositePatentsTo(other.name, otherAvg, dontMatch, threshold, limit));
            else lists.addAll(findSimilarPatentsTo(other.name, otherAvg, dontMatch, threshold, limit));

        } catch(SQLException sql) {
        }
        return lists;
    }

    /*private static void mergePatentLists(List<PatentList> patentLists, int limit) {
        PriorityQueue<AbstractPatent> queue = new PriorityQueue<>();
        for(PatentList list: patentLists) {
            queue.addAll(list.getPatents());
        }
        patentLists.clear();
        patentLists.add(new PatentList(new ArrayList<>(queue).subList(Math.max(0,queue.size()-limit-1), queue.size()-1)));
    }*/



    // returns null if patentNumber not found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        assert patentNumber!=null : "Patent number is null!";
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new ArrayList<>();
        long startTime = System.currentTimeMillis();
        if(patentNamesToExclude ==null) {
            patentNamesToExclude=new HashSet<>();
            if(patentNumber!=null)patentNamesToExclude.add(patentNumber);
        }
        final Set<String> otherSet = Collections.unmodifiableSet(patentNamesToExclude);

        setupMinHeap(limit);
        List<PatentList> lists = Arrays.asList(similarPatentsHelper(patentList,avgVector, otherSet, name, patentNumber, threshold, limit));

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");

        return lists;
    }

    public Double angleBetweenPatents(String name1, String name2, Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        INDArray first = getVectorFromDB(name1,vocab);
        INDArray second = getVectorFromDB(name2,vocab);
        if(first!=null && second!=null) {
            //valid
            return Transforms.cosineSim(first,second);
        } else return null;
    }

    // returns null if patentNumber not found
    public List<PatentList> findOppositePatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        List<PatentList> toReturn = findSimilarPatentsTo(patentNumber, avgVector.mul(-1.0), patentNamesToExclude, threshold, limit);
        for(PatentList l : toReturn) {
            l.flipAvgSimilarity();
            l.getPatents().forEach(p->p.flipSimilarity());
        }
        return toReturn;
    }

    private static INDArray getVectorFromDB(String patentNumber,INDArray eigenVectors,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        INDArray avgVector = null;
        // first look in own patent list
        if(globalCache.containsKey(patentNumber)) avgVector=globalCache.get(patentNumber);

        if(avgVector==null) {
            ResultSet rs = Database.getBaseVectorFor(patentNumber);
            if (!rs.next()) {
                return null; // nothing found
            }
            int offset = 1;
            avgVector = handleResultSet(rs, offset, vocab);
            if(avgVector!=null) globalCache.put(patentNumber,avgVector);
        }
        if(eigenVectors!=null&&avgVector!=null) avgVector.mmuli(eigenVectors);
        return avgVector;
    }

    public static INDArray getVectorFromDB(String patentNumber,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        return getVectorFromDB(patentNumber, null, vocab);
    }

    private synchronized PatentList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Set<String> patentNamesToExclude, String name1, String name2, double threshold, int limit) {
        Patent.setBaseVector(baseVector);
        //AtomicDouble total = new AtomicDouble(0.0);
        //AtomicInteger cnt = new AtomicInteger(0);
        patentList.forEach(patent -> {
            if(patent!=null&&!patentNamesToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget();
                //total.getAndAdd(patent.getSimilarityToTarget());
                //cnt.getAndIncrement();
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            //String assignee = assigneeMap.get(p.getName());
            //if(assignee==null)assignee="";
            try {
                resultList.add(0, Patent.abstractClone(p, null));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        }
        //double avgSim = cnt.get() > 0 ? total.get()/cnt.get() : 0.0;
        PatentList results = new PatentList(resultList,name1,name2,Transforms.cosineSim(baseVector,computeAvg(patentList,name1)));
        return results;
    }

    // unit test!
    public static void main(String[] args) throws Exception {
        /*try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File("candidateSets/3"), "othername");
            System.out.println("Most similar: ");
            PatentList list;// = finder.findSimilarPatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
            System.out.println("Most opposite: ");
            list = finder.findOppositePatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }



            System.out.println("Candidate set comparison: ");
            list = finder.similarFromCandidateSet(new SimilarPatentFinder(null, new File("candidateSets/2"), "name"),0.0,20,false).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }*/
    }
}
