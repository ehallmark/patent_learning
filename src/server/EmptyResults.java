package server;

/**
 * Created by ehallmark on 7/27/16.
 */
public class EmptyResults extends ServerResponse {
    EmptyResults(String query) {
        super(query,"No similar patents found.",null);
    }
}
