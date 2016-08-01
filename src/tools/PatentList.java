package tools;

import server.AbstractPatent;

import java.io.Serializable;
import java.util.List;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList implements Serializable {
    private static final long serialVersionUID = 1L;
    private String bySimilarityTo;
    private List<AbstractPatent> patents;
    public PatentList(List<AbstractPatent> patentList, String type) {
        this.patents=patentList;
        bySimilarityTo=type;
    }
}
