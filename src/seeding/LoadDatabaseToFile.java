package seeding;

import java.io.*;
import java.util.*;

import org.deeplearning4j.text.documentiterator.LabelledDocument;

public class LoadDatabaseToFile {
	private MyPreprocessor preprocessor;

	public LoadDatabaseToFile() throws Exception {
		// Training Data
		preprocessor = new MyPreprocessor();
		DatabaseIterator iter = new DatabaseIterator(false);

		if(!new File(Constants.COMPDB_TECHNOLOGIES_INTEGER_TO_STRING_MAP).exists()) serializeToFile(iter.initializeTechnologyHash(),new File(Constants.COMPDB_TECHNOLOGIES_INTEGER_TO_STRING_MAP));

		System.out.println("Starting Training Patents");
		serializeToFile(downloadFiles(Constants.COMPDB_TRAIN_FOLDER, iter), new File(Constants.COMPDB_TRAIN_LABEL_FILE));

		
		// Testing Data
		DatabaseIterator testIter = new DatabaseIterator(true);
		System.out.println("Starting Testing Patents");
		serializeToFile(downloadFiles(Constants.COMPDB_TEST_FOLDER, testIter),new File(Constants.COMPDB_TEST_LABEL_FILE));

	}

	public void serializeToFile(Object toWrite, File file) throws IOException {
		System.out.println("Serializing object to "+file.getName()+"...");
		ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
		oos.writeObject(toWrite);
		oos.flush();
		oos.close();
	}

	public Map<String, Set<String>> downloadFiles(String rootFolderName, DatabaseIterator iterator) throws IOException {
		Map<String, Set<String>> toReturn = new HashMap<>();
		new File(rootFolderName).mkdir();
		while(iterator.hasNextDocuments()) {
			List<LabelledDocument> documents = iterator.nextDocuments();
			String patentNumber = iterator.getCurrentPatentNumber();
			Set<String> labels = new HashSet<>(iterator.getCurrentLabels());
			toReturn.put(patentNumber,labels);
			File folder = new File(rootFolderName+patentNumber);
			if(!(folder.exists()&&folder.isDirectory())) {
				// make folder
				folder.mkdirs();
			}
			for(LabelledDocument doc : documents) {
				if(doc==null||doc.getContent()==null)continue;
				String type = ((PatentDocument)doc).getType();
				String fName = rootFolderName + patentNumber + "/" + ((PatentDocument) doc).getType();
				String content;
				if(type.equals(Constants.ABSTRACT)||type.equals(Constants.DESCRIPTION)) {
					StringJoiner document = new StringJoiner("\n");
					for (String sentence : doc.getContent().split("\\.")) {
						document.add(preprocessor.preProcess(sentence));
					}
					content = document.toString();
				} else {
					content = doc.getContent();
				}
				if(!new File(fName).exists())writeToFile(fName, preprocessor.preProcess(content));
				System.out.println(fName);
			}
		}
		return toReturn;
	}
	
	public void writeToFile(String filename, String contents) throws IOException {
		if(contents == null || contents.length() < 10) return;
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filename)));
		bw.write(contents);
		bw.flush();
		bw.close();
	}
	
	public static void main(String[] args) {
		try {
			new LoadDatabaseToFile();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
