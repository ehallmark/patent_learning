package server;

import analysis.SimilarPatentFinder;
import com.google.gson.Gson;
import spark.Request;
import seeding.Database;

import static spark.Spark.post;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    public static SimilarPatentFinder finder;
    private static boolean failed = false;
    private static int DEFAULT_LIMIT = 25;
    static {
        try {
            Database.setupSeedConn();
            finder = new SimilarPatentFinder();
        } catch(Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

    public static void server() {
        post("/similar_patents", (req, res) -> {
            res.type("application/json");
            String pubDocNumber = req.queryParams("patent");
            if(pubDocNumber == null) return new Gson().toJson(new NoPatentProvided());
            int limit = extractLimit(req);
            return new Gson().toJson(new PatentResponse(finder.findSimilarPatentsTo(pubDocNumber, limit)));
        });
    }

    private static int extractLimit(Request req) {
        try {
            return Integer.valueOf(req.queryParams("limit"));
        } catch(Exception e) {
            e.printStackTrace();
            return DEFAULT_LIMIT;
        }
    }

    public static void main(String[] args) {
        assert !failed : "Failed to load similar patent finder!";
        server();
    }
}