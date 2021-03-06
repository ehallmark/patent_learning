package server.tools;

import analysis.Classification;
import analysis.WordFrequencyPair;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import tools.PatentList;
import static j2html.TagCreator.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse {

    public PatentResponse(List<PatentList> patents, boolean findDissimilar, Pair<String,List<WordFrequencyPair<String,Float>>> keyWordListWithName, double timeToComplete, List<Classification> autoClassifications, int depth) {
        super("PATENT_RESPONSE", to_html_table(patents, findDissimilar, keyWordListWithName, timeToComplete,autoClassifications,depth).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, boolean findDissimilar, Pair<String,List<WordFrequencyPair<String,Float>>> keyWordListWithName, double time, List<Classification> autoClassifications, int depth) {
        // List
        Tag classTags = null;
        if(autoClassifications!=null) {
            assert depth >= 1 : "Must have some depth";
            classTags = div().with(img().attr("src", "/images/most_recent_tree.gif?"+new Date().getTime()),br(),br(),
                    Classification.getTable(depth,autoClassifications.stream().sorted().map(c->c.toTableRow()).collect(Collectors.toList())),br(),br());
        }
        Tag keywords = null;
        if (keyWordListWithName != null) {
            List<Tag> headers = Arrays.asList(tr().with(th().attr("colspan", "2").attr("style", "text-align: left;").with(h3().with(label("Predicted Key Phrases for "+keyWordListWithName.getFirst())))), tr().with(th("Phrase").attr("style", "text-align: left;"), th("Score").attr("style", "text-align: left;")));
            keywords = table().with(headers).with(
                    keyWordListWithName.getSecond().stream().map(k -> tr().with(td(k.getFirst()), td(k.getSecond().toString()))).collect(Collectors.toList())
            ).with(br(),br());
        }
        String similarName = findDissimilar ? "Dissimilar" : "Similar";
        Tag patents = null;
        if (patentLists != null) {
            patents = div().with(patentLists.stream().sorted().map(patentList ->
                            table().with(
                                    thead().with(
                                            tr().with(th().attr("colspan", "3").attr("style", "text-align: left;").with(
                                                    h3().with(label(similarName + " " + patentList.getName1() + " to " + patentList.getName2()))
                                            )),
                                            tr().with(th().attr("colspan", "3").attr("style", "text-align: left;").with(
                                                    label("Distributed Average Similarity: " + patentList.getAvgSimilarity())
                                            )),
                                            tr().with(
                                                    th("Patent #").attr("style", "text-align: left;"),
                                                    th("Cosine Similarity").attr("style", "text-align: left;"),
                                                    th("Invention Title").attr("style", "text-align: left;")
                                            )
                                    ),
                                    tbody().with(
                                            patentList.getPatents().stream().sorted((o1, o2) -> findDissimilar ? Double.compare(o1.getSimilarity(), o2.getSimilarity()) : Double.compare(o2.getSimilarity(), o1.getSimilarity())
                                            ).map(patent ->
                                                    tr().with(td().with(a(patent.getName()).withHref("https://www.google.com/patents/US" + patent.getName().split("\\s+")[0])), td(Double.toString(patent.getSimilarity())), td(patent.getTitle()))
                                            ).collect(Collectors.toList())

                            ), br(),br())
            ).collect(Collectors.toList()));
            //if (findDissimilar) Collections.reverse(patents);
        }
        return div().with(
                div().with(label(Double.toString(time)+" seconds to complete.")),br(),
                div().with(Arrays.asList(keywords,patents,classTags).stream().filter(t->t!=null).collect(Collectors.toList()))
        );

    }
}
