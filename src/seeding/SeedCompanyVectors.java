package seeding;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/11/16.
 */
public class SeedCompanyVectors {
    private static final String USER_AGENT = "Mozilla/5.0";

    // HTTP POST request
    private static void createCandidateSetPost(String candidateSetName, String assignee, String patents) throws Exception {
        assert candidateSetName!=null && !(assignee==null && patents==null);
        String url = "http://192.168.1.148:4567/create";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = null;
        if(patents==null || patents.length() == 0) urlParameters = "name="+candidateSetName+"&assignee="+assignee;
        else if(assignee == null || assignee.trim().length() == 0) {
            urlParameters = "name="+candidateSetName+"&patents="+patents;
            //if(innerNames!=null&&innerNames.size()>0) urlParameters+="&names="+String.join(">><<",innerNames);
        }
        else throw new RuntimeException("Invalid parameters!");

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        //System.out.println(response.toString());

    }

    private static void createCandidateGroupPost(String groupPrefix) throws Exception {
        assert groupPrefix!=null;
        String url = "http://192.168.1.148:4567/create_group";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = "group_prefix="+groupPrefix;

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        //System.out.println(response.toString());

    }

    public static void main(String[] args) throws Exception {
        createCandidateSetPost("Huawei", "huawei", null);
        createCandidateSetPost("Panasonic", "panasonic", null);
        createCandidateSetPost("Sony", "sony", null);
        createCandidateSetPost("ZTE", "zte", null);
        createCandidateSetPost("Orange", "orange", null);
        createCandidateSetPost("Cisco", "cisco", null);
        createCandidateSetPost("Telia Custom", null, String.join(" ",Arrays.asList(Constants.CUSTOM_TELIA_PATENT_LIST.split("\\s+"))));

        createCandidateSetPost("Verizon", "verizon", null);
        // ETSI PATENTS!
        createCandidateSetPost("ETSI (all)", null, String.join(" ",Constants.ETSI_PATENT_LIST));

        {
            String ETSI_PREFIX = "ETSI -";
            createCandidateGroupPost(ETSI_PREFIX);
            Map<String, List<String>> ETSIMap = GetEtsiPatentsList.getETSIPatentMap();
            for (Map.Entry<String, List<String>> e : ETSIMap.entrySet()) {
                createCandidateSetPost(ETSI_PREFIX+" "+e.getKey(), null, String.join(" ", e.getValue()));
            }
        }

        {
            String COMPDB_PREFIX = "CompDB -";
            createCandidateGroupPost(COMPDB_PREFIX);
            Map<String, List<String>> compDBMap = Database.getCompDBMap();
            for (Map.Entry<String, List<String>> e : compDBMap.entrySet()) {
                createCandidateSetPost(COMPDB_PREFIX+" "+e.getKey(), null, String.join(" ", e.getValue()));
            }
        }

        /*
        System.out.println("Trying to get Gather patents...");
        // gather patents
        {
            Map<String, List<String>> gatherDBTechMap = Database.getGatherTechMap();
            for (Map.Entry<String, List<String>> e : gatherDBTechMap.entrySet()) {
                sendPost("Gather Tech - " + e.getKey(), null, String.join(" ", e.getValue()));
            }
        }

        {
            Map<String, List<String>> gatherDBEOUMap = Database.getGatherEOUMap();
            for (Map.Entry<String, List<String>> e : gatherDBEOUMap.entrySet()) {
                sendPost("Gather EOU - " + e.getKey(), null, String.join(" ", e.getValue()));
            }
        }
        */

    }
}
