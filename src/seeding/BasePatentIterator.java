package seeding;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements LabelAwareIterator {

    private final int startDate;
    private ResultSet resultSet;
    private String currentPatent;
    private SentencePreProcessor preProcessor;
    private Iterator<String> currentPatentIterator;
    private LabelsSource source;

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        this.preProcessor = new MyPreprocessor();
        Database.setupSeedConn();
        reset();
        setupLabelsSource();
        reset();
    }

    public void setupLabelsSource() throws SQLException {
        List<String> labels = Collections.synchronizedList(new LinkedList<>());
        ResultSet rs = Database.getPatentNumbersAfter(startDate);
        while(rs.next()) {
            labels.add(rs.getString(1));
        }
        source = new LabelsSource(labels);
    }

    public void resetQuery() throws SQLException {
        resultSet = Database.getPatentVectorData(startDate);
    }


    protected void setCurrentPatent() throws SQLException {
        currentPatent = resultSet.getString(1);
    }

    protected Iterator<String> processedSentenceIterator() throws SQLException {
        List<String> preIterator = new LinkedList<>();

        // Abstract
        String abstractText = preProcessor.preProcess(resultSet.getString(2));
        if(!shouldRemoveSentence(abstractText))preIterator.add(abstractText);

        // Description
        String descriptionText = preProcessor.preProcess(resultSet.getString(3));
        if(!shouldRemoveSentence(descriptionText))preIterator.add(descriptionText.substring(0, descriptionText.lastIndexOf(" ")));
        return preIterator.iterator();
    }


    public boolean shouldRemoveSentence(String str) {
        if(str==null)return true;
        boolean wasChar = false;
        int wordCount = 0;
        for(Character c : str.toCharArray()) {
            if(Character.isSpaceChar(c) && wasChar) {
                wordCount++;
                wasChar = false;
            } else if(Character.isAlphabetic(c)) {
                wasChar = true;
            }
            if(wordCount >= Constants.MIN_WORDS_PER_SENTENCE) return false;
        }
        return true;
    }

    @Override
    public boolean hasNextDocument() {
        try {
            return ((currentPatentIterator == null || currentPatentIterator.hasNext()) || (resultSet == null || !(resultSet.isAfterLast() || resultSet.isLast())));
        } catch (SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR WHILE ITERATING");
        }
    }

    @Override
    public LabelledDocument nextDocument() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                LabelledDocument doc = new LabelledDocument();
                doc.setLabel(currentPatent);
                doc.setContent(currentPatentIterator.next());
                return doc;
            }
            // Check for more results in result set
            resultSet.next();

            setCurrentPatent();
            currentPatentIterator = processedSentenceIterator();
            //  System.out.println("Number of sentences for "+currentPatent+": "+preIterator.size());
            return nextDocument();

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    @Override
    public void reset() {
        try {
            if(resultSet!=null && !resultSet.isClosed()) resultSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            resetQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("UNABLE TO RESET QUERY");
        }
        currentPatentIterator=null;
    }

    @Override
    public LabelsSource getLabelsSource() {
        return source;
    }


}
