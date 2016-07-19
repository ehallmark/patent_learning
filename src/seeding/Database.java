package seeding;

import java.sql.*;
import java.util.*;


public class Database {
	private static final String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://192.168.1.148/compdb_production?user=postgres&password=&tcpKeepAlive=true";
	private static Connection seedConn;
	private static Connection mainConn;
	private static Connection compDBConn;
	private static final String addOrUpdateWord = "INSERT INTO patent_words (word,count) VALUES (?,1) ON CONFLICT (word) DO UPDATE SET (count)=(patent_words.count+1) WHERE patent_words.word=?";
	private static final String valuablePatentsQuery = "SELECT distinct r.pub_doc_number from patent_assignment as p join patent_assignment_property_document as q on (p.assignment_reel_frame=q.assignment_reel_frame) join patent_grant as r on (q.doc_number=r.pub_doc_number) join patent_grant_maintenance as m on (r.pub_doc_number=m.pub_doc_number) where conveyance_text like 'ASSIGNMENT OF ASSIGNOR%' and pub_date > to_char(now()::date, 'YYYYMMDD')::int-100000 AND (doc_kind='B1' or doc_kind='B2') group by r.pub_doc_number having (not array_agg(trim(trailing ' ' from maintenance_event_code))&&'{\"EXP.\"}'::text[]) AND array_length(array_agg(distinct recorded_date),1) > 2";
	private static final String unValuablePatentsQuery = "SELECT p.pub_doc_number from patent_grant as p join patent_grant_maintenance as q on (p.pub_doc_number=q.pub_doc_number) and pub_date > to_char(now()::date, 'YYYYMMDD')::int-100000 group by p.pub_doc_number having (array_agg(trim(trailing ' ' from maintenance_event_code))&&'{\"EXP.\"}'::text[])";
	private static final String patentVectorStatement = "SELECT pub_doc_number, abstract, substring(description FROM 1 FOR ?) FROM patent_grant WHERE pub_date >= ? AND (abstract IS NOT NULL AND description IS NOT NULL)";
	private static final String patentVectorWithTitleAndDateStatement = "SELECT pub_doc_number, pub_date, invention_title, abstract, substring(description FROM 1 FOR ?) FROM patent_grant WHERE pub_date >= ? AND (abstract IS NOT NULL AND description IS NOT NULL AND invention_title IS NOT NULL)";
	private static final String distinctClassificationsStatement = "SELECT distinct main_class FROM us_class_titles";
	private static final String classificationsFromPatents = "SELECT pub_doc_number, array_agg(distinct substring(classification_code FROM 1 FOR 3)) FROM patent_grant_uspto_classification WHERE pub_doc_number=ANY(?) AND classification_code IS NOT NULL group by pub_doc_number";
	private static final String allPatentsAfterGivenDate = "SELECT array_agg(pub_doc_number) FROM patent_grant where pub_date > ? group by pub_date";
	private static final String insertPatentVectorsQuery = "INSERT INTO patent_vectors (pub_doc_number,pub_date,invention_title_wv,abstract_pv,description_pv) VALUES (?,?,?,?,?) ON CONFLICT(pub_doc_number) DO UPDATE SET (pub_date,invention_title_wv,abstract_pv,description_pv)=(?,?,?,?) WHERE patent_vectors.pub_doc_number=?";

	public static void setupMainConn() throws SQLException {
		mainConn = DriverManager.getConnection(patentDBUrl);
		mainConn.setAutoCommit(false);
	}

	public static void setupSeedConn() throws SQLException {
		seedConn = DriverManager.getConnection(patentDBUrl);
		seedConn.setAutoCommit(false);
	}

	public static void setupCompDBConn() throws SQLException {
		compDBConn = DriverManager.getConnection(compDBUrl);
		compDBConn.setAutoCommit(false);
	}

	public static void commit() {
		try {
			mainConn.commit();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public static void close(){
		try {
			if(mainConn!=null && !mainConn.isClosed())mainConn.close();
			if(seedConn!=null && !seedConn.isClosed())seedConn.close();
			if(compDBConn!=null && !compDBConn.isClosed())compDBConn.close();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public static void insertPatentVectors(String pub_doc_number,int pub_date, Double[] invention_title, Double[] abstract_vectors, Double[] description) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(insertPatentVectorsQuery);
		Array invention_array = mainConn.createArrayOf("float8", invention_title);
		Array abstract_array = mainConn.createArrayOf("float8", abstract_vectors);
		Array description_array = mainConn.createArrayOf("float8", description);
		ps.setString(1, pub_doc_number);
		ps.setInt(2, pub_date);
		ps.setArray(3, invention_array);
		ps.setArray(4, abstract_array);
		ps.setArray(5, description_array);
		ps.setInt(6, pub_date);
		ps.setArray(7, invention_array);
		ps.setArray(8, abstract_array);
		ps.setArray(9, description_array);
		ps.setString(10, pub_doc_number);
		ps.executeUpdate();
	}

	public static ResultSet getPatentsAfter(int pubDate) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(allPatentsAfterGivenDate);
		ps.setInt(1, pubDate);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet getClassificationsAndTitleFromList(List<String> list) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("SELECT invention_title, array_agg(distinct class), array_agg(distinct subclass) from patent_grant as p join patent_grant_uspto_classification as q on (p.pub_doc_number=q.pub_doc_number) WHERE p.pub_doc_number=ANY(?) and q.pub_doc_number=ANY(?) AND invention_title is not null GROUP BY p.pub_doc_number");
		Array docNums = seedConn.createArrayOf("varchar", list.toArray());
		ps.setArray(1, docNums);
		ps.setArray(2, docNums);
		ps.setFetchSize(10);
		return ps.executeQuery();
	}

	public static ResultSet getClassificationsFromPatents(Array patentArray) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(classificationsFromPatents);
		ps.setArray(1,patentArray);
		ps.setFetchSize(5);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet getPatentVectorData(int startDate) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(patentVectorStatement);
		ps.setInt(1, Constants.MAX_DESCRIPTION_LENGTH);
		ps.setInt(2,startDate);
		ps.setFetchSize(5);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet getPatentDataWithTitleAndDate(int startDate) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(patentVectorWithTitleAndDateStatement);
		ps.setInt(1, Constants.MAX_DESCRIPTION_LENGTH);
		ps.setInt(2,startDate);
		ps.setFetchSize(5);
		System.out.println(ps);
		return ps.executeQuery();
	}


	public static int getNumberOfCompDBClassifications() throws SQLException {
		PreparedStatement ps = compDBConn.prepareStatement("SELECT COUNT(DISTINCT id) FROM technologies WHERE name is not null and char_length(name) > 0 and id != ANY(?)");
		ps.setArray(1, compDBConn.createArrayOf("int4",Constants.BAD_TECHNOLOGY_IDS.toArray()));
		System.out.println(ps);
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			return rs.getInt(1);
		} else {
			throw new RuntimeException("Unable to get number of compdb classifications!");
		}
	}

	public static List<String> getDistinctClassifications() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(distinctClassificationsStatement);
		ps.setFetchSize(100);
		ResultSet rs = ps.executeQuery();
		List<String> distinctClassifications = new ArrayList<>();
		Set<String> duplicationCheck = new HashSet<>();
		while(rs.next()) {
			String klass = rs.getString(1).trim();
			if(!duplicationCheck.contains(klass)) {
				distinctClassifications.add(klass);
				duplicationCheck.add(klass);
			}
		}
		ps.close();
		Collections.sort(distinctClassifications);
		return distinctClassifications;
	}


	public static ResultSet getValuablePatents() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(valuablePatentsQuery);
		ps.setFetchSize(10);
		return ps.executeQuery();
	}

	public static ResultSet getUnValuablePatents() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(unValuablePatentsQuery);
		ps.setFetchSize(10);
		return ps.executeQuery();
	}


	public static void addOrUpdateWord(String word) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(addOrUpdateWord);
		ps.setString(1, word);
		ps.setString(2, word);
		ps.executeUpdate();
	}
	
	public static ResultSet getStopWords(int limit) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("SELECT word FROM patent_words ORDER BY count DESC limit ?");
		ps.setInt(1,limit);
		return ps.executeQuery();
	}


}
