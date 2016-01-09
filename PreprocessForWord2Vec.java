/**
 * 
 */
package analyzer;
import java.io.*;
import java.util.*;
import json.JSONArray;
import json.JSONException;
import json.JSONObject;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;
//import org.tartarus.snowball.ext.porterStemmer;

import structures.Post;

/**
 * @author hongning
 * Sample codes for demonstrating OpenNLP package usage 
 * NOTE: the code here is only for demonstration purpose, 
 * please revise it accordingly to maximize your implementation's efficiency!
 */
public class DocAnalyzer {
	
	//a list of stopwords
	int loadControl=0;
	int reviews_size=0;
	String reviewsFile="reviewsFile.csv";
	PrintWriter reviewsWriter;
	//static HashSet<String> all_stopwords;
	//static HashSet<String> original_stopwords;
	//static HashSet<String> new_stopwords;
	//you can store the loaded reviews in this arraylist for further processing
	List<Post> m_reviews;
	List<HashMap<String,Integer>> reviewsTokenTF;
	List<double[]> reviewsVector;
	List<Map<String,Double>> reviewsSimilarities;
	
	Tokenizer tokenizer; // global tokenizer
	SnowballStemmer stemmer; // global stemmer
	HashMap<String,Integer> token_TTF;
	HashMap<String,Integer> token_DF;
	HashMap<String,Integer> controlVocabulary;
	HashMap<String,Double> controlVocabulary_IDF;
	List<String> unigramList;
	List<int[]> bigramList;
	HashMap<String,Integer> unigramIndex;
	HashMap<String,Integer> unigramCount;
	int numUnigram;
	int numBigram;
	double[] unigramLM;
	int[][] bigramCount;
	double[][] bigramLM_LIS; // Linear Interpolation Smoothing
	double[][] bigramLM_ADS; // Absolute Discount Smoothing
	double lamda;	// for Linear Interpolation Smoothing
	double cigma;	// for Absolute Discount Smoothing
	
	int numLoadedFiles = 0；
	int maxNumLoadedFiles = 2000;
	StringBuilder resultSb;
	
	public DocAnalyzer(){
		//all_stopwords=new HashSet<String>();
		//original_stopwords=new HashSet<String>();
		//new_stopwords=new HashSet<String>();
		m_reviews = new ArrayList<Post>();
		reviewsTokenTF=new ArrayList<HashMap<String,Integer>>();
		
		reviewsVector=new ArrayList<double[]>();
		reviewsSimilarities=new ArrayList<Map<String,Double>>();
		stemmer = new englishStemmer();
		try{
			tokenizer = new TokenizerME(new TokenizerModel(new FileInputStream("./data/Model/en-token.bin")));
		}catch(IOException e){
			e.printStackTrace();
		}
		token_TTF=new HashMap<String,Integer>();
		token_DF=new HashMap<String,Integer>();
		controlVocabulary=new HashMap<String,Integer>();
		controlVocabulary_IDF=new HashMap<String,Double>();
		unigramList=new ArrayList<String>();
		unigramIndex=new HashMap<String,Integer>();
		unigramCount=new HashMap<String,Integer>();
		bigramList=new ArrayList<int[]>();
		numUnigram=0;
		numBigram=0;
		lamda=0.9; cigma=0.1;
		
		try{
			reviewsWriter = new PrintWriter(new BufferedWriter(new FileWriter(reviewsFile, true)));
		}catch (IOException e) {
			e.printStackTrace();
		}
		
		resultSb = new StringBuilder();
	}
	
	//sample code for loading a list of stopwords from file
	//you can manually modify the stopword file to include your newly selected words
	
	
	public void analyzeDocumentDemo(JSONObject json){
		try {
			PrintWriter writer = new PrintWriter("temp.txt", "UTF-8");
			JSONArray jarray = json.getJSONArray("Reviews"); int j=0;
			for(int i=0; i<jarray.length(); i++) {
				Post review = new Post(jarray.getJSONObject(i));
				//System.out.println(review.getID()+"  "+review.getAuthor());
				//List<String> tokens=TokenizerNormalizationStemming(review.getContent()); 
				j=TokenizerNormalizationStemming(review.getContent()); 
				// HINT: perform necessary text processing here, e.g., tokenization, stemming and normalization
				if(j>0){
					//m_reviews.add(review);
					reviews_size++;
					reviewsWriter.println(review.getAuthor()+","+review.getDate()+","+review.getContent());
				}
			}
			writer.close();
        }catch(FileNotFoundException ex){
            ex.printStackTrace();
        }catch(IOException e){
        	e.printStackTrace();
        }catch(JSONException e){
			e.printStackTrace();
		}
	}
	
	
	public String LoadReadMeFile(String fileName) {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String str;
			while ((str = in.readLine()) != null){
				str = TokenizerNormalizationStemming(str);
				sb.append(str);
			}
			in.close();
			return sb.toString();
		}catch(FileNotFoundException ex) {
            ex.printStackTrace();
        }catch(IOException e) {
        	e.printStackTrace();
        }
	}
	
	
	public String parseConnectedWords(String text) {
		StringBuilder sb = new StringBuilder();
		int start = 0;
		for(int i = 0; i < text.length(); i++) {
			if(Character.isUpperCase(text.charAt(i))) {
				sb.append(text.substring(start, i).toLowerCase());
				sb.append(" ");
				start = i;
			}
		}
		return sb.toString();
	}
	
	public String parseImportedPackages(String text) {
		String[] tokens = text.split("[^a-zA-Z']+");
		StringBuilder sb = new StringBuilder();
		for(String token : tokens) {
			if(token.equals(token.toLowerCase()) || token.equals(token.toUpperCase())) {
				sb.append(token.toLowerCase());
				sb.append(" ");
			} else {
				sb.append(parseConnectedWords(token)); 
			}
		}
		
		return sb.toString();
	}
	
	public String parseClassMethods(String text) {
		
	}
	
	public String loadCodeFile(String fileName) {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String str = in.readLine();
			str = str.trim();  // remove the leading and trailing spaces.
			while(str != null) {
				if(str.startsWith("import")) {
					
				}else if(str.startsWith("public")) {
					
				}else if(str.startsWith("private")) {
					
				}else if(str.startsWith("class")) {
					
				}else if(str.startsWith("void")) {
					
				}
				str = TokenizerNormalizationStemming(str);
				sb.append(str);
				
				str = in.readLine();
				str = str.trim();
			}
			in.close();
			return sb.toString();
		}catch(FileNotFoundException ex) {
            ex.printStackTrace();
        }catch(IOException e) {
        	e.printStackTrace();
        }
	}
	
	// recursively load files in a directory 
	public void LoadFilesFromFolder(String folder, String prefix, String suffix) {
		File dir = new File(folder); 
		for(File f:dir.listFiles()){
			if(f.isFile() && f.getName().endsWith(suffix)) {
				if(f.getName().toLowerCase().startsWith(prefix)) {
					System.out.println(numLoadedFiles + " load README file"+" : " + f.getName());
					resultSb.append(LoadReadMeFile(f.getName()));
					numLoadedFiles ++;
				}
				else if(f.getName().endsWith(suffix)) {
					System.out.println(numLoadedFiles + " load code file"+" : "+f.getName());
					
					numLoadedFiles ++;
				}
				
			}
			else if(f.isDirectory())
				LoadFilesFromFolder(f.getAbsolutePath(), prefix, suffix);
		}
		reviewsWriter.close();
	}
	
	public String TokenizerNormalizationStemming(String text){
		if(text == null || text.length() == 0)return "";
		StringBuilder sb = new StringBuilder();
		for(String token : tokenizer.tokenize(text)) {
			//token=token.replaceAll("\\W+", "");
			token = token.replaceAll("\\p{Punct}+","");
			token = token.toLowerCase();
			if(token.matches(".*\\d+.*")) token = "";
			if(!token.isEmpty()){
				stemmer.setCurrent(token);
				if(stemmer.stem()) token = stemmer.getCurrent();
				sb.append(token);
				sb.append(" ");
			}
		}
		return sb.toString();
	}
	
	
	// sort HashMap by value
	private static Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap) {
		// Convert Map to List
		List<Map.Entry<String, Integer>> list =new LinkedList<Map.Entry<String, Integer>>(unsortMap.entrySet());
		// Sort list with comparator, to compare the Map values
		Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
			public int compare(Map.Entry<String, Integer> o1,
                                           Map.Entry<String, Integer> o2) {
				return -1*(o1.getValue()).compareTo(o2.getValue());
			}
		});
		// Convert sorted map back to a Map
		Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
		for (Iterator<Map.Entry<String, Integer>> it = list.iterator(); it.hasNext();) {
			Map.Entry<String, Integer> entry = it.next();
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}
	
	private static Map<String, Double> sortByComparatorDouble(Map<String, Double> unsortMap) {
		// Convert Map to List
		List<Map.Entry<String, Double>> list =new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());
		// Sort list with comparator, to compare the Map values
		Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> o1,
                                           Map.Entry<String, Double> o2) {
				return -1*(o1.getValue()).compareTo(o2.getValue());
			}
		});
		// Convert sorted map back to a Map
		Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
		for (Iterator<Map.Entry<String, Double>> it = list.iterator(); it.hasNext();) {
			Map.Entry<String, Double> entry = it.next();
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}
	

	public static void saveMapToFile(String fileName,Map<Object,Object> map) {
		try{
			PrintWriter writer=new PrintWriter(fileName, "UTF-8");
			for(Object key:map.keySet()){
				writer.println(key+" "+map.get(key));
			}
			writer.close();
		}catch(FileNotFoundException ex){
            ex.printStackTrace();
        }catch(IOException e){
        	e.printStackTrace();
        }
	}
	
	public static void main(String[] args)throws IOException{		
		
		DocAnalyzer analyzer = new DocAnalyzer();
		
		analyzer.LoadDirectory("C:/Dropbox/CS/Courses/InformationRetrieval/MP1/yelp", "json");
		analyzer.unigramLanguageModel();
		
		System.out.println("Done ");
	}
}

