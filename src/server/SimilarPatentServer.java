package server;

import analysis.Classification;
import analysis.KMeansCalculator;
import analysis.SimilarPatentFinder;
import analysis.WordFrequencyPair;
import com.google.gson.Gson;
import j2html.tags.Tag;
import jxl.Workbook;
import jxl.write.WritableWorkbook;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import scratch.TreeDrawing;
import seeding.*;
import server.tools.*;
import spark.Request;
import spark.Response;
import spark.Session;
import tools.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;

import static j2html.TagCreator.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder globalFinder;
    private static boolean failed = false;
    private static int DEFAULT_LIMIT = 10;
    private static final String SELECT_CANDIDATE_FORM_ID = "select-candidate-form";
    private static final String NEW_CANDIDATE_FORM_ID = "new-candidate-form";
    private static final String SELECT_BETWEEN_CANDIDATES_FORM_ID = "select-between-candidates-form";
    private static final String SELECT_ANGLE_BETWEEN_PATENTS = "select-angle-between-patents-form";
    private static final String SELECT_KEYWORDS = "select-keywords-form";
    private static final String AUTO_CLASSIFY = "auto-classify-form";
    private static Map<Integer, Pair<Boolean, String>> candidateSetMap;
    private static Map<Integer, List<Integer>> groupedCandidateSetMap;
    private static final Map<String,Pair<Float,INDArray>> vocab;
    private static TokenizerFactory tokenizer = new DefaultTokenizerFactory();
    static {
        tokenizer.setTokenPreProcessor(new MyPreprocessor());
        Map<String,Pair<Float,INDArray>> vocabCopy = null;
        try {
            vocabCopy = Collections.unmodifiableMap(BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE)));
            Database.setupSeedConn();
            Database.setupMainConn();
        } catch(Exception e) {
            e.printStackTrace();
            failed = true;
        }
        vocab = vocabCopy;
    }

    private static void loadBaseFinder() {
        if(!failed)
            try {
                globalFinder = new SimilarPatentFinder(vocab);
            } catch(Exception e) {
                e.printStackTrace();
                failed=true;
            }
    }

    private static String getAndRemoveMessage(Session session) {
        String message = session.attribute("message");
        if(message!=null)session.removeAttribute("message");
        return message;
    }

    public static void server() {
        get("/", (req, res) -> templateWrapper(res, div().with(selectCandidateForm(), hr()), getAndRemoveMessage(req.session())));

        get("/new", (req, res) -> templateWrapper(res, createNewCandidateSetForm(), getAndRemoveMessage(req.session())));

        post("/auto_classify", (req, res) -> {
            long startTime = System.currentTimeMillis();
            String idstr = req.queryParams("name");
            if(idstr==null||idstr.trim().length()<=0) return new Gson().toJson(new SimpleAjaxMessage("Please include candidate set selection!"));

            String kstr = req.queryParams("k");
            Integer k = null;
            if(kstr==null||kstr.trim().length()==0)  k = 5;
            else k = Integer.valueOf(kstr);

            String nstr = req.queryParams("numPredictions");
            Integer n = null;
            if(nstr==null||nstr.trim().length()==0)  n=1;
            else n = Integer.valueOf(nstr);

            String istr = req.queryParams("iterations");
            Integer i = null;
            if(istr==null||istr.trim().length()==0)  i=128;
            else i = Integer.valueOf(istr);

            String depthStr = req.queryParams("depth");
            Integer depth = null;
            if(depthStr==null||depthStr.trim().length()==0)  depth=1;
            else depth = Integer.valueOf(depthStr);

            String ngramStr = req.queryParams("ngram");
            Integer ngram = null;
            if(ngramStr==null||ngramStr.trim().length()==0)  ngram=3;
            else ngram = Integer.valueOf(ngramStr);


            String equal = req.queryParams("equal");
            boolean isEqual = false;
            if(equal!=null&&equal.trim().length()>0&&equal.contains("on"))  isEqual=true;

            Integer id = Integer.valueOf(idstr);

            if(id==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to find candidate set"));
            if(id < 0) return new Gson().toJson(new SimpleAjaxMessage("Cannot choose default candidate set!"));
            if(groupedCandidateSetMap.containsKey(id)) return new Gson().toJson(new SimpleAjaxMessage("Cannot choose a pre-grouped candidate set"));

            // otherwise we are good to go
            String name = candidateSetMap.get(id).getSecond();
            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), name,vocab);
            try {
                Pair<TreeNode<KMeansCalculator>,List<Classification>> classifications = finder.autoClassify(vocab,k,n,isEqual,i,ngram,depth);
                // Handle csv or json
                classifications.getFirst().getData().setClassString(name+"\n");
                File imgFile = new File("images/most_recent_tree.gif");
                if(imgFile.exists())imgFile.delete();
                PatentResponse response = new PatentResponse(null, false, null, new Double(System.currentTimeMillis()-startTime)/1000, classifications.getSecond(),depth);
                writeTreeToOutputStream(new FileOutputStream(imgFile),classifications.getFirst(),depth+1,k);
                if (responseWithCSV(req)) {
                    res.type("text/csv");
                    return CSVHelper.to_csv(response);
                } else {
                    res.type("application/json");
                    return new Gson().toJson(response);
                }
            } catch (Exception e) {
                res.type("application/json");
                return new Gson().toJson(new SimpleAjaxMessage(e.toString()));
            } finally {
                SimilarPatentFinder.clearKeywordCache();
            }

        });


        post("/create_group", (req, res) ->{
            if(req.queryParams("group_prefix")==null || req.queryParams("group_prefix").trim().length()==0) {
                req.session().attribute("message", "Invalid form parameters.");
                res.redirect("/new");
            } else {
                try {
                    String name = req.queryParams("group_prefix");
                    Database.createCandidateGroup(name);
                    req.session().attribute("message", "Candidate group created.");
                    res.redirect("/");

                } catch(SQLException sql) {
                    sql.printStackTrace();
                    req.session().attribute("message", "Database Error");
                    res.redirect("/new");

                }
            }
            return null;
        });


        post("/create", (req, res) -> {
            if(req.queryParams("name")==null || (req.queryParams("patents")==null && req.queryParams("assignee")==null)) {
                req.session().attribute("message", "Invalid form parameters.");
                res.redirect("/new");
            } else {
                try {
                    String name = req.queryParams("name");
                    int id = Database.createCandidateSetAndReturnId(name);
                    // try to get percentages from form
                    File file = new File(Constants.CANDIDATE_SET_FOLDER+id);
                    if(req.queryParams("assignee")!=null&&req.queryParams("assignee").trim().length()>0) {
                        new SimilarPatentFinder(Database.selectPatentNumbersFromAssignee(req.queryParams("assignee")),file,name,vocab);
                    } else if (req.queryParams("patents")!=null&&req.queryParams("patents").trim().length()>0) {
                        new SimilarPatentFinder(preProcess(req.queryParams("patents")), file, name,vocab);
                    } else {
                        req.session().attribute("message", "Patents and Assignee parameters were blank. Please choose one to fill out");
                        res.redirect("/new");
                        return null;
                    }
                    //new Emailer("Sucessfully created "+name);
                    req.session().attribute("message", "Candidate set created.");
                    res.redirect("/");

                } catch(SQLException sql) {
                    sql.printStackTrace();
                    req.session().attribute("message", "Database Error");
                    res.redirect("/new");

                }
            }
            return null;
        });

        // Host my own image asset!
        get("/images/brand.png", (request, response) -> {
            response.type("image/png");

            String pathToImage = "images/brand.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        // Host my own image asset!
        get("/images/most_recent_tree.gif", (request, response) -> {
            response.type("image/gif");

            String pathToImage = "images/most_recent_tree.gif";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "gif", out);
            out.close();
            response.status(200);
            return response.body();
        });

        post("/similar_candidate_sets", (req, res) -> {
            long startTime = System.currentTimeMillis();

            res.type("application/json");
            if(req.queryParams("name1")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first candidate set."));
            if(req.queryParamsValues("name2")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a second candidate set."));

            List<String> otherIds = Arrays.asList(req.queryParamsValues("name2"));
            if(otherIds.isEmpty()) return new Gson().toJson(new SimpleAjaxMessage("Must choose at least one other candidate set"));
            Integer id1 = Integer.valueOf(req.queryParams("name1"));
            if(id1 < 0 && globalFinder==null)
                return new Gson().toJson(new SimpleAjaxMessage("Unable to find first candidate set."));
            else {
                // both exist
                int limit = extractLimit(req);
                System.out.println("\tLimit: " + limit);
                List<SimilarPatentFinder> firstFinders = new ArrayList<>();
                if (id1 >= 0) {
                    if(groupedCandidateSetMap.containsKey(id1)) {
                        for(Integer id : groupedCandidateSetMap.get(id1)) {
                            String name = candidateSetMap.get(id).getSecond();
                            firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), name,vocab));
                        }
                    } else {
                        String name1 = candidateSetMap.get(id1).getSecond();
                        firstFinders.add(new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id1), name1,vocab));
                    }
                } else firstFinders = Arrays.asList(globalFinder);
                List<SimilarPatentFinder> secondFinders = new ArrayList<>();
                for(String id : otherIds) {
                    SimilarPatentFinder finder = null;
                    if (Integer.valueOf(id) >= 0) {
                        try {
                            if(groupedCandidateSetMap.containsKey(Integer.valueOf(id))) {
                                for(Integer groupedId : groupedCandidateSetMap.get(Integer.valueOf(id))) {
                                    System.out.println("CANDIDATE LOADING: " + candidateSetMap.get(groupedId).getSecond());
                                    finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + groupedId), candidateSetMap.get(groupedId).getSecond(),vocab);
                                    if (finder != null && finder.getPatentList() != null && !finder.getPatentList().isEmpty()) {
                                        secondFinders.add(finder);
                                    }
                                }
                            } else {
                                System.out.println("CANDIDATE LOADING: " + candidateSetMap.get(Integer.valueOf(id)).getSecond());
                                finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), candidateSetMap.get(Integer.valueOf(id)).getSecond(),vocab);
                                if (finder != null && finder.getPatentList() != null && !finder.getPatentList().isEmpty()) {
                                    secondFinders.add(finder);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    } else {
                        secondFinders.add(globalFinder);
                    }
                }
                List<PatentList> patentLists = new ArrayList<>();
                double threshold = extractThreshold(req);
                boolean findDissimilar = extractFindDissimilar(req);
                for(SimilarPatentFinder first : firstFinders) {
                    patentLists.addAll(first.similarFromCandidateSets(secondFinders, threshold, limit, findDissimilar));
                }
                System.out.println("SIMILAR PATENTS FOUND!!!");
                patentLists.forEach(list->{
                    System.out.println("Sim "+list.getName1()+" "+list.getName2()+": "+list.getAvgSimilarity());
                    list.getPatents().forEach(p->{
                        System.out.println(p.getName()+": "+p.getSimilarity());
                    });
                });
                PatentResponse response = new PatentResponse(patentLists,findDissimilar,null,new Double(System.currentTimeMillis()-startTime)/1000,null,-1);
                return new Gson().toJson(response);
            }

        });

        post("/angle_between_patents", (req,res) ->{
            res.type("application/json");
            if(req.queryParams("name1")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a first patent."));
            if(req.queryParams("name2")==null)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a second patent."));

            String name1 = req.queryParams("name1");
            String name2 = req.queryParams("name2");

            if(name1==null || name2==null) return new Gson().toJson(new SimpleAjaxMessage("Please include two patents!"));

            Double sim = globalFinder.angleBetweenPatents(name1, name2,vocab);

            if(sim==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to find both patent vectors"));
            return new Gson().toJson(new SimpleAjaxMessage("Similarity between "+name1+" and "+name2+" is "+sim.toString()));
        });

        post("/similar_patents", (req, res) -> {
            long startTime = System.currentTimeMillis();
            ServerResponse response;
            String pubDocNumber = req.queryParams("patent");
            String text = req.queryParams("text");
            if((pubDocNumber == null || pubDocNumber.trim().length()==0) && (text==null||text.trim().length()==0)) return new Gson().toJson(new NoPatentProvided());
            boolean findDissimilar = extractFindDissimilar(req);
            if(req.queryParamsValues("name")==null || req.queryParamsValues("name").length==0)  return new Gson().toJson(new SimpleAjaxMessage("Please choose a candidate set."));
            List<PatentList> patents=new ArrayList<>();
            SimilarPatentFinder currentPatentFinder = pubDocNumber!=null&&pubDocNumber.trim().length()>0 ? new SimilarPatentFinder(pubDocNumber,vocab) : new SimilarPatentFinder("Custom Text", new WordVectorizer(vocab).getVector(text));
            if(currentPatentFinder.getPatentList()==null) return new Gson().toJson(new SimpleAjaxMessage("Unable to calculate vectors"));
            System.out.println("Searching for: " + pubDocNumber);
            int limit = extractLimit(req);
            boolean averageCandidates = extractAverageCandidates(req);
            System.out.println("\tLimit: " + limit);
            double threshold = extractThreshold(req);
            Arrays.asList(req.queryParamsValues("name")).forEach(name->{
                Integer id = null;
                try {
                    id = Integer.valueOf(name);
                    if(id!=null&&id>=0) {
                        if(groupedCandidateSetMap.containsKey(id)) {
                            // grouped
                            for (Integer integer : groupedCandidateSetMap.get(id)) {
                                SimilarPatentFinder finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + integer), candidateSetMap.get(integer).getSecond(),vocab);
                                if (finder.getPatentList() != null && !finder.getPatentList().isEmpty())
                                    if(averageCandidates) {
                                        patents.addAll(currentPatentFinder.similarFromCandidateSet(finder, threshold, limit, findDissimilar));
                                    } else {
                                        patents.addAll(finder.similarFromCandidateSet(currentPatentFinder, threshold, limit, findDissimilar));
                                    }
                            }

                        } else {
                            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File(Constants.CANDIDATE_SET_FOLDER + id), candidateSetMap.get(id).getSecond(),vocab);
                            if (finder.getPatentList() != null && !finder.getPatentList().isEmpty())
                                if(averageCandidates) {
                                    patents.addAll(currentPatentFinder.similarFromCandidateSet(finder, threshold, limit, findDissimilar));
                                } else {
                                    patents.addAll(finder.similarFromCandidateSet(currentPatentFinder, threshold, limit, findDissimilar));
                                }
                        }
                    } else {
                        patents.addAll(globalFinder.similarFromCandidateSet(currentPatentFinder,threshold,limit,findDissimilar));
                    }
                } catch(Exception e) {
                    // bad format or something
                    e.printStackTrace();
                }
            });
            if(patents==null) response=new PatentNotFound(pubDocNumber);
            else if(patents.isEmpty()) response=new EmptyResults(pubDocNumber);
            else response=new PatentResponse(patents,findDissimilar,null,new Double(System.currentTimeMillis()-startTime)/1000,null,-1);

            // Handle csv or json
            if(responseWithCSV(req)) {
                res.type("text/csv");
                return CSVHelper.to_csv(response);
            } else {
                res.type("application/json");
                return new Gson().toJson(response);
            }
        });


        post("/keywords", (req, res) -> {
            long startTime = System.currentTimeMillis();
            ServerResponse response;
            String pubDocNumber = req.queryParams("patent");
            String text = req.queryParams("text");
            if ((pubDocNumber == null || pubDocNumber.trim().length() == 0) && (text == null || text.trim().length() == 0))
                return new Gson().toJson(new NoPatentProvided());
            int limit = extractLimit(req);
            String ngramStr = req.queryParams("ngram");
            Integer ngram = null;
            if(ngramStr==null||ngramStr.trim().length()==0)  ngram=3;
            else ngram = Integer.valueOf(ngramStr);

            List<WordFrequencyPair<String, Float>> patents = pubDocNumber == null || pubDocNumber.trim().length() == 0 ? SimilarPatentFinder.predictKeywords(text, limit, vocab, ngram) : SimilarPatentFinder.predictKeywords(limit, vocab, pubDocNumber, ngram);
            try {
                if (patents == null) response = new PatentNotFound(pubDocNumber);
                else if (patents.isEmpty()) response = new EmptyResults(pubDocNumber);
                else
                    response = new PatentResponse(null, false, new Pair<>(pubDocNumber == null || pubDocNumber.trim().length() == 0 ? "Custom Text" : pubDocNumber, patents), new Double(System.currentTimeMillis() - startTime) / 1000, null,-1);
                // Handle csv or json
                if (responseWithCSV(req)) {
                    res.type("text/csv");
                    return CSVHelper.to_csv(response);
                } else {
                    res.type("application/json");
                    return new Gson().toJson(response);
                }
            } catch(Exception e) {
                res.type("application/json");
                return new Gson().toJson(new SimpleAjaxMessage(e.toString()));
            }
        });


    }

    private static void writeTreeToOutputStream(OutputStream stream, TreeNode<KMeansCalculator> tree, int depth, int k) throws IOException{
        // Drawing Examples
        TreeGui<KMeansCalculator> c = new TreeGui<>(tree,depth,k);
        c.draw();
        boolean success = c.writeToOutputStream(stream);
        System.out.println("Created " + tree.getClass().getSimpleName() + " : " + success);
        stream.close();
    }

    private static List<String> preProcess(String str) {
        return Arrays.asList(str.split("\\s+"));
    }

    private static Tag templateWrapper(Response res, Tag form, String message) {
        res.type("text/html");
        if(message==null)message="";
        return html().with(
                head().with(
                        //title(title),
                        script().attr("src","https://ajax.googleapis.com/ajax/libs/jquery/3.0.0/jquery.min.js"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().attr("OnKeyPress","return disableKeyPress(event);").with(
                        div().attr("style", "width:80%; padding: 2% 10%;").with(
                                a().attr("href", "/").with(
                                        img().attr("src", "/images/brand.png")
                                ),
                                //h2(title),
                                hr(),
                                h4(message),
                                hr(),
                                form,
                                div().withId("results"),
                                br(),
                                br(),
                                br()
                        )
                )
        );
    }

    private static Tag formScript(String formId, String url, String buttonText) {
        return script().withText(
                "$(document).ready(function() { "
                          + "$('#"+formId+"').submit(function(e) {"
                            + "$('#"+formId+"-button').attr('disabled',true).text('"+buttonText+"ing...');"
                            + "var url = '"+url+"'; "
                            + "$.ajax({"
                            + "  type: 'POST',"
                            + "  url: url,"
                            + "  data: $('#"+formId+"').serialize(),"
                            + "  success: function(data) { "
                            + "    $('#results').html(data.message); "
                            //+ "    alert(data.message);"
                            + "    $('#"+formId+"-button').attr('disabled',false).text('"+buttonText+"');"
                            + "  }"
                            + "});"
                            + "e.preventDefault(); "
                          + "});"
                        + "});");
    }


    private static void importCandidateSetFromDB() throws SQLException {
        Map<String,List<Integer>> groupedSetNameMap = new HashMap<>();
        ResultSet rs = Database.selectGroupedCandidateSets();
        while(rs.next()) {
            groupedSetNameMap.put(rs.getString(1),new ArrayList<>());
        }
        rs.close();
        ResultSet candidates = Database.selectAllCandidateSets();
        while(candidates.next()) {
            String name = candidates.getString(1);
            AtomicBoolean hidden = new AtomicBoolean(false);
            for(Map.Entry<String,List<Integer>> e : groupedSetNameMap.entrySet()) {
                if(name.startsWith(e.getKey())) {
                    hidden.set(true);
                    e.getValue().add(candidates.getInt(2));
                    break;
                }
            }
            candidateSetMap.put(candidates.getInt(2),new Pair<>(hidden.get(),name));
        }
        candidates.close();
        int size = Collections.max(candidateSetMap.keySet())+1;
        for(Map.Entry<String,List<Integer>> e : groupedSetNameMap.entrySet()) {
            if(e.getValue().size()>0) {
                candidateSetMap.put(size,new Pair<>(false, e.getKey()));
                groupedCandidateSetMap.put(size, e.getValue());
                size++;
            }
        }
    }

    private static Tag selectCandidateSetDropdown() {
        return selectCandidateSetDropdown("Select Candidate Set", "name", true);
    }

    private static Tag selectCandidateSetDropdown(String label, String name, boolean multiple) {
        candidateSetMap = new HashMap<>();
        groupedCandidateSetMap = new HashMap<>();
        candidateSetMap.put(-1, new Pair<>(false,"**ALL**")); // adds the default candidate set
        try {
            importCandidateSetFromDB();
        } catch(SQLException sql ) {
            sql.printStackTrace();
            return label("ERROR:: Unable to load candidate set.");
        }
        return div().with(
                label(label),
                br(),
                (multiple ? (select().attr("multiple","true")) : (select())).withName(name).with(
                        candidateSetMap.entrySet().stream().sorted((o1,o2)->Integer.compare(o1.getKey(),o2.getKey())).map(entry->{if(entry.getValue().getFirst()) {return null; } if(entry.getKey()<0) return option().withText(entry.getValue().getSecond()).attr("selected","true").withValue(entry.getKey().toString()); else return option().withText(entry.getValue().getSecond()).withValue(entry.getKey().toString());}).filter(t->t!=null).collect(Collectors.toList())
                )
        );
    }

    private static Tag selectCandidateForm() {
        return div().with(formScript(SELECT_CANDIDATE_FORM_ID, "/similar_patents", "Search"),
                formScript(SELECT_BETWEEN_CANDIDATES_FORM_ID, "/similar_candidate_sets", "Search"),
                formScript(SELECT_ANGLE_BETWEEN_PATENTS, "/angle_between_patents", "Search"),
                formScript(SELECT_KEYWORDS, "/keywords", "Search"),
                formScript(AUTO_CLASSIFY, "/auto_classify", "Classify"),
                table().with(
                        tbody().with(
                                tr().attr("style", "vertical-align: top;").with(
                                        td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Find Similarity between two Patents"),
                                                form().withId(SELECT_ANGLE_BETWEEN_PATENTS).with(
                                                        label("Patent 1"),br(),input().withType("text").withName("name1"),br(),
                                                        label("Patent 2"),br(),input().withType("text").withName("name2"),br(),
                                                        br(),
                                                        button("Search").withId(SELECT_ANGLE_BETWEEN_PATENTS+"-button").withType("submit")
                                                ),
                                                h3("Predict Keywords and Key Phrases"),
                                                form().withId(SELECT_KEYWORDS).with(
                                                        label("Patent"),br(),input().withType("text").withName("patent"),br(),
                                                        label("Text"),br(),textarea().withName("text"),br(),
                                                        label("Max length of n grams"),br(),input().withType("text").withName("ngram"),br(),
                                                        label("Limit"),br(),input().withType("text").withName("limit"),br(),
                                                        br(),
                                                        button("Search").withId(SELECT_KEYWORDS+"-button").withType("submit")
                                                ),
                                                h3("Auto Classify Candidate Set"),
                                                form().withId(AUTO_CLASSIFY).with(
                                                        selectCandidateSetDropdown("Select Candidate Set","name",false),
                                                        label("Number of Clusters"),br(),input().withType("text").withName("k"),br(),
                                                        label("Number of Tags per Cluster"),br(),input().withType("text").withName("numPredictions"),br(),
                                                        label("Number of Iterations"),br(),input().withType("text").withName("iterations"),br(),
                                                        label("Maximum depth"),br(),input().withType("text").withName("depth"),br(),
                                                        label("Max length of n grams"),br(),input().withType("text").withName("ngram"),br(),
                                                        label("Force equal subset size"),br(),input().withType("checkbox").withName("equal"),br(),br(),
                                                        button("Classify").withId(AUTO_CLASSIFY+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Find Similar Patents By Patent"),
                                                form().withId(SELECT_CANDIDATE_FORM_ID).with(selectCandidateSetDropdown(),
                                                        label("Similar To Patent"),br(),input().withType("text").withName("patent"),br(),
                                                        label("Similar To Custom Text"),br(),textarea().withName("text"),br(),
                                                        label("Average candidates"),br(),input().withType("checkbox").withName("averageCandidates"),br(),
                                                        label("Limit"),br(),input().withType("text").withName("limit"),br(),
                                                        label("Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                        label("Find most dissimilar"),br(),input().withType("checkbox").withName("findDissimilar"),br(),br(),
                                                        button("Search").withId(SELECT_CANDIDATE_FORM_ID+"-button").withType("submit")
                                                )
                                        ),td().attr("style","width:33%; vertical-align: top;").with(
                                                h3("Find Similar Patents to Candidate Set 2"),
                                                form().withId(SELECT_BETWEEN_CANDIDATES_FORM_ID).with(selectCandidateSetDropdown("Candidate Set 1","name1",false),
                                                        selectCandidateSetDropdown("Candidate Set 2", "name2",true),
                                                        label("Limit"),br(),input().withType("text").withName("limit"), br(),
                                                        label("Threshold"),br(),input().withType("text").withName("threshold"),br(),
                                                        label("Find most dissimilar"),br(),input().withType("checkbox").withName("findDissimilar"),br(),br(),
                                                        button("Search").withId(SELECT_BETWEEN_CANDIDATES_FORM_ID+"-button").withType("submit")
                                                )
                                        )
                                )
                        )
                ),
                br(),
                br(),
                a("Or create a new Candidate Set").withHref("/new")
        );
    }


    private static Tag createNewCandidateSetForm() {
        return form().withId(NEW_CANDIDATE_FORM_ID).withAction("/create").withMethod("post").with(
                p().withText("(May take awhile...)"),
                label("Name"),br(),
                input().withType("text").withName("name"),
                br(),
                label("Seed By Assignee"),br(),
                input().withType("text").withName("assignee"),
                br(),
                label("Or By Patent List (space separated)"), br(),
                textarea().withName("patents"), br(),
                button("Create").withId(NEW_CANDIDATE_FORM_ID+"-button").withType("submit")
        );
    }

    private static boolean responseWithCSV(Request req) {
        String param = req.queryParams("format");
        return (param!=null && param.contains("csv"));
    }

    private static int extractLimit(Request req) {
        try {
            return Integer.valueOf(req.queryParams("limit"));
        } catch(Exception e) {
            System.out.println("No limit parameter specified... using default");
            return DEFAULT_LIMIT;
        }
    }

    private static boolean extractAverageCandidates(Request req) {
        try {
            return (req.queryParams("averageCandidates")==null||!req.queryParams("averageCandidates").startsWith("on")) ? false : true;
        } catch(Exception e) {
            System.out.println("No averageCandidates parameter specified... using default");
            return false;
        }
    }

    private static double extractThreshold(Request req) {
        try {
            return Double.valueOf(req.queryParams("threshold"));
        } catch(Exception e) {
            System.out.println("No threshold parameter specified... using default");
            return -1D;
        }
    }

    private static boolean extractFindDissimilar(Request req) {
        try {
            return (req.queryParams("findDissimilar")==null||!req.queryParams("findDissimilar").startsWith("on")) ? false : true;
        } catch(Exception e) {
            System.out.println("No findDissimilar parameter specified... using default");
            return false;
        }
    }

    public static void main(String[] args) {
        if(!Arrays.asList(args).contains("dontLoadBaseFinder")) {
            loadBaseFinder();
            assert !failed : "Failed to load similar patent finder!";
        }
        server();
    }
}
