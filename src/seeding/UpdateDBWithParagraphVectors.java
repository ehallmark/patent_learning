package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/23/16.
 */
public class UpdateDBWithParagraphVectors {
    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();
        Database.setupInsertConn();

        ParagraphVectors vectors = WordVectorSerializer.readParagraphVectorsFromText(new File(Constants.WORD_VECTORS_PATH));
        WeightLookupTable<VocabWord> lookupTable = vectors.lookupTable();

        ResultSet rs = Database.selectRawPatents();
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            String patentNumber = rs.getString(1);
            INDArray vec = lookupTable.vector(patentNumber);
            if(vec!=null) {
                Float[] dbVec = VectorHelper.toObject(vec.data().asFloat());
                Database.updateParagraphVectorFor(patentNumber, dbVec);
                System.out.println(cnt.getAndIncrement());
            }
        }
        Database.insertCommit();
        Database.close();
    }
}