package seeding;

import analysis.Patent;

import java.util.*;

public class Constants {
	public static final int DEFAULT_MIN_WORD_FREQUENCY = 5;
	public static final int MAX_DESCRIPTION_LENGTH = 20000;
	public static final int MIN_WORDS_PER_SENTENCE = 5;
	public static final int VECTOR_LENGTH = 300;
	//public static final int NUM_ROWS_OF_WORD_VECTORS = 5;
	public static final int START_DATE = 20010000;
	public static final int VOCAB_START_DATE = 20100000;

	public static final Set<Integer> BAD_TECHNOLOGY_IDS = new HashSet<>(Arrays.asList(new Integer[]{136,182,301,316,519,527}));


	public static final String[] STOP_WORDS = new String[]{
			"a", "about", "above", "above", "across", "after", "afterwards", "again", "against", "all", "almost", "alone", "along", "already", "also","although","always","am","among", "amongst", "amoungst", "amount",  "an", "and", "another", "any","anyhow","anyone","anything","anyway", "anywhere", "are", "around", "as",  "at", "back","be","became", "because","become","becomes", "becoming", "been", "before", "beforehand", "behind", "being", "below", "beside", "besides", "between", "beyond", "bill", "both", "bottom","but", "by", "call", "can", "cannot", "cant", "co", "con", "could", "couldnt", "cry", "de", "describe", "detail", "do", "done", "down", "due", "during", "each", "eg", "eight", "either", "eleven","else", "elsewhere", "empty", "enough", "etc", "even", "ever", "every", "everyone", "everything", "everywhere", "except", "few", "fifteen", "fify", "fill", "find", "fire", "first", "five", "for", "former", "formerly", "forty", "found", "four", "from", "front", "full", "further", "get", "give", "go", "had", "has", "hasnt", "have", "he", "hence", "her", "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how", "however", "hundred", "ie", "if", "in", "inc", "indeed", "interest", "into", "is", "it", "its", "itself", "keep", "last", "latter", "latterly", "least", "less", "ltd", "made", "many", "may", "me", "meanwhile", "might", "mill", "mine", "more", "moreover", "most", "mostly", "move", "much", "must", "my", "myself", "name", "namely", "neither", "never", "nevertheless", "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "often", "on", "once", "one", "only", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "own","part", "per", "perhaps", "please", "put", "rather", "re", "same", "see", "seem", "seemed", "seeming", "seems", "serious", "several", "she", "should", "show", "side", "since", "sincere", "six", "sixty", "so", "some", "somehow", "someone", "something", "sometime", "sometimes", "somewhere", "still", "such", "system", "take", "ten", "than", "that", "the", "their", "them", "themselves", "then", "thence", "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "thick", "thin", "third", "this", "those", "though", "three", "through", "throughout", "thru", "thus", "to", "together", "too", "top", "toward", "towards", "twelve", "twenty", "two", "un", "under", "until", "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "whither", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves", "the"
	};
	
	public static final Set<String> STOP_WORD_SET = new HashSet<String>(Arrays.asList(STOP_WORDS));

	public static final String GOOGLE_WORD_VECTORS_PATH = "GoogleNews-vectors-negative300.bin";
	public static final String CANDIDATE_SET_FOLDER = "candidateSets/";
	public static final String WORD_VECTORS_PATH = "word_vector_model.txt";
	public static final String VOCAB_FILE = "vocabulary_for_word_vectors.txt";
	public static final String CLAIM_VOCAB_FILE = "claim_vocabulary.txt";
	public static final String PATENT_VECTOR_LIST_FILE = "patent_vector_list.obj";

	public static final String TITLE_VECTORS = "invention_title_vectors";
	public static final String ABSTRACT_VECTORS = "abstract_vectors";
	public static final String DESCRIPTION_VECTORS = "description_vectors";
	public static final String CLAIM_VECTORS = "claims_vectors";
	public static final String CLASS_VECTORS = "class_vectors";
	public static final String SUBCLASS_VECTORS = "subclass_vectors";

	public static final String PATENT_VECTOR_TYPE = "patent_vectors";
	public static final String CLAIM_VECTOR_TYPE = "claim_vectors";
	public static final String CLASSIFICATION_VECTOR_TYPE = "classification_vectors";

	public static final String SIMILARITY_MODEL_FILE="similarity_model.txt";
	public static final String COMPDB_TECHNOLOGY_MODEL_FILE="compdb_technology_model.txt";
	public static final String VALUABLE_PATENT_MODEL_FILE = "valuable_patent_model.txt";
	//public static final String ASSIGNEE_MAP_FILE = "patent_assignee_map.txt";


	//public static final List<String> DEFAULT_2D_VECTORS = Arrays.asList(Constants.ABSTRACT_VECTORS, Constants.DESCRIPTION_VECTORS,Constants.CLAIM_VECTORS);
	// Claim vectors are not working yet...
	public static final List<String> DEFAULT_2D_VECTORS = Arrays.asList(Constants.CLAIM_VECTORS);
	public static final List<String> DEFAULT_1D_VECTORS = Arrays.asList(Constants.TITLE_VECTORS,Constants.ABSTRACT_VECTORS,Constants.DESCRIPTION_VECTORS,Constants.CLASS_VECTORS, Constants.SUBCLASS_VECTORS);
	public static final List<Patent.Type> VECTOR_TYPES = Arrays.asList(Patent.Type.TITLE, Patent.Type.ABSTRACT, Patent.Type.DESCRIPTION, Patent.Type.CLASS, Patent.Type.SUBCLASS, Patent.Type.CLAIM);
	public static final int NUM_2D_VECTORS = DEFAULT_2D_VECTORS.size();
	public static final int NUM_1D_VECTORS = DEFAULT_1D_VECTORS.size();

	public static final Map<Patent.Type, Double> VECTOR_PERCENTAGES = new HashMap<>();
	static {
		VECTOR_PERCENTAGES.put(Patent.Type.ABSTRACT, 0.05);
		VECTOR_PERCENTAGES.put(Patent.Type.CLAIM, 0.60);
		VECTOR_PERCENTAGES.put(Patent.Type.DESCRIPTION,0.2);
		VECTOR_PERCENTAGES.put(Patent.Type.CLASS, 0.05);
		VECTOR_PERCENTAGES.put(Patent.Type.SUBCLASS, 0.05);
		VECTOR_PERCENTAGES.put(Patent.Type.TITLE, 0.05);
	}



	public static final String CUSTOM_TELIA_PATENT_LIST = "8457598 8396974 8166180 8250091 8385242 8355695 8326955 8929333 8930574 8520509 9049624 9088621 8614988 8774751 8775642 8943567 8923903 9137384 8990960 8983459 9215594 6993666 6934391 6882844 6237093 6711262 6880001 6768895 6925568 7224975 7113800 6990332 7010694 8005068 7221959 7516222 7711378 8332532 7836190 8095685 8331866 6208387 6397933 7065199 6368205 7036141 7212543 7003579 7853979 8184596 7894338";

}
