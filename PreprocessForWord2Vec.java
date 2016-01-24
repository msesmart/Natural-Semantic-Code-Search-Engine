/**
 *  	Author: 	Chengjun Yuan  
 *			<cy3yb@virginia.edu>
 *	Time:		Jan. 2016 
 *	Purpose:	extract words from readme files and code files of a number of java projects.
 *	HowToRun:	compile:	javac -cp '.:libstemmer.jar' PreprocessForWord2Vec.java
			run:		java -cp '.:libstemmer.jar' PreprocessForWord2Vec projectsFolder
 */
 
import java.io.*;
import java.util.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

public class PreprocessForWord2Vec {
	int numLoadedFiles = 0;
	int maxNumLoadedFiles = 2000;
	StringBuilder codeSb, readMeSb;
	SnowballStemmer stemmer;
	HashMap<String, Integer> wordsMap;
	HashSet<String> stopWords;
 
	public PreprocessForWord2Vec(){
		
		/*try{
			reviewsWriter = new PrintWriter(new BufferedWriter(new FileWriter(reviewsFile, true)));
		}catch (IOException e) {
			e.printStackTrace();
		}*/
		
		codeSb = new StringBuilder();
		readMeSb = new StringBuilder();
		stemmer = new englishStemmer();
		wordsMap = new HashMap<String, Integer>();
		stopWords = new HashSet<String>();
		 
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
			return null;
        }catch(IOException e) {
        	e.printStackTrace();
			return null;
        }
	}
	
	public String parseConnectedWords(String text) {
		StringBuilder sb = new StringBuilder();
		int start = 0; int i = 0;
		for(i = 0; i < text.length(); i++) {
			if(Character.isUpperCase(text.charAt(i))) {
				String token = text.substring(start, i).toLowerCase();
				stemmer.setCurrent(token);
				if(stemmer.stem()) token = stemmer.getCurrent();
				sb.append(token); sb.append(" ");
				start = i;
			}
		}
		String token = text.substring(start, i).toLowerCase();
		stemmer.setCurrent(token);
		if(stemmer.stem()) token = stemmer.getCurrent();
		sb.append(token); sb.append(" ");
		return sb.toString();
	}
	
	public String parseImportedPackages(String text) {
		String[] tokens = text.split("[^a-zA-Z']+");
		StringBuilder sb = new StringBuilder();
		for(String token : tokens) {
			if(token.equals(token.toLowerCase()) || token.equals(token.toUpperCase())) {
				stemmer.setCurrent(token.toLowerCase());
				if(stemmer.stem()) token = stemmer.getCurrent();
				sb.append(token);
				sb.append(" ");
			} else {
				sb.append(parseConnectedWords(token)); 
			}
		}
		return sb.toString();
	}
	
	public String parseClassMethods(String text) {
		String[] tokens = text.split("[^a-zA-Z']+");
		StringBuilder sb = new StringBuilder();
		for(String token : tokens) {
			if(token.equals(token.toLowerCase()) || token.equals(token.toUpperCase())) {
				stemmer.setCurrent(token.toLowerCase());
				if(stemmer.stem()) token = stemmer.getCurrent();
				sb.append(token);
				sb.append(" ");
			} else {
				sb.append(parseConnectedWords(token)); 
			}
		}
		return sb.toString();
	}
	
	public String loadCodeFile(String fileName) {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String str = in.readLine();
			
			while(str != null) {
				str = str.trim();  // remove the leading and trailing spaces.
				if(str.startsWith("import")) {
					sb.append(parseImportedPackages(str));
				}else if(str.startsWith("public") || str.startsWith("private") || str.startsWith("class") || str.startsWith("void")) {
					sb.append(parseClassMethods(str));
				} else ;				
				str = in.readLine();
			}
			in.close();
			return sb.toString();
		}catch(FileNotFoundException ex) {
            ex.printStackTrace();
			return null;
        }catch(IOException e) {
        	e.printStackTrace();
			return null;
        }
	}
	
	// recursively load files in a directory 
	public void LoadFilesFromFolder(String folder, String prefix, String suffix) {
		File dir = new File(folder); 
		for(File f:dir.listFiles()){
			if(f.isFile()) {
				if(f.getName().toLowerCase().startsWith(prefix)) {
					//System.out.println(numLoadedFiles + " load README file"+" : " + f.getName());
					readMeSb.append(LoadReadMeFile(f.getAbsolutePath()));
					numLoadedFiles ++;
				}
				else if(f.getName().endsWith(suffix)) {
					//System.out.println(numLoadedFiles + " load code file"+" : "+f.getName());
					codeSb.append(loadCodeFile(f.getAbsolutePath()));
					numLoadedFiles ++;
				}
			}
			else if(f.isDirectory())
				LoadFilesFromFolder(f.getAbsolutePath(), prefix, suffix);
		}
		//reviewsWriter.close();
	}
	
	// save String data into file under folder 
	public void saveStringIntoFileUnderFolder(String folder, String fileName, String str) {
		try {
            File dir = new File(folder); 
			File file = new File(dir, fileName);
            FileOutputStream is = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(is);    
            Writer w = new BufferedWriter(osw);
            w.write(str);
            w.close();
        } catch (IOException e) {
            System.err.println("Problem writing to the file statsTest.txt");
        }
	}
	
	// parse each project folder and save the preprocessed data in their own folder.
	public void parseProjects(String folder) {
		File dir = new File(folder); 
		int i = 1;
		for(File f:dir.listFiles()){ 
			/* detect project folder */
			if(f.isDirectory()) {
				System.out.println("project #"+ String.valueOf(i) + f.getAbsolutePath());
				readMeSb.setLength(0);
				codeSb.setLength(0);
				LoadFilesFromFolder(f.getAbsolutePath(), "readme", "java");
				saveStringIntoFileUnderFolder(f.getAbsolutePath(), "re.prepro", readMeSb.toString());
				saveStringIntoFileUnderFolder(f.getAbsolutePath(), "code.prepro", codeSb.toString());
			}
			i ++;
			//if(i >= 2) break;
		}
	}
	
	public String TokenizerNormalizationStemming(String text){
		if(text == null || text.length() == 0)return "";
		StringBuilder sb = new StringBuilder();
		String[] tokens = text.split("[^a-zA-Z']+");
		for(String token : tokens) {
			//token=token.replaceAll("\\W+", "");
			//token = token.replaceAll("\\p{Punct}+","");
			token = token.toLowerCase();
			//if(token.matches(".*\\d+.*")) token = "";
			if(!token.isEmpty()){
				stemmer.setCurrent(token);
				if(stemmer.stem()) token = stemmer.getCurrent();
				sb.append(token);
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	public void WordsStatics(String folder) {
		try {
			System.out.println("start words statics...");
			File dir = new File(folder); 
			for(File f:dir.listFiles()){ 
				/* detect project folder */
				if(f.isDirectory()) {
					String line = new String(Files.readAllBytes(new File(f, "re.prepro").getAbsolutePath()));
					String[] tokens = line.split(" ");
					for(String token : tokens) {
						if(token != null && token.length() > 0) {
							if(wordsMap.containsKey(token)) wordsMap.put(token, wordsMap.get(token) + 1);
							else wordsMap.put(token, 1);
						}
					}
					line = new String(Files.readAllBytes(new File(f, "code.prepro").getAbsolutePath()));
					tokens = line.split(" ");
					for(String token : tokens) {
						if(token != null && token.length() > 0) {
							if(wordsMap.containsKey(token)) wordsMap.put(token, wordsMap.get(token) + 1);
							else wordsMap.put(token, 1);
						}
					}
				}
			}
			System.out.println("Finish words statics !! ");
		} catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	public void generateStopWords(String stopWordsFileName, int offset) {
		wordsMap = sortByComparator(wordsMap); // sort the wordsMap based on value
		try {
			/* write wordsStatics into file */
			PrintWriter writer = new PrintWriter("wordsStatics.txt", "UTF-8");
			for(Map.Entry<String, Integer> entry : wordsMap.entrySet()) {
				writer.println(entry.key() + "," + String.valueOf(entry.value()));
				if(entry.value() > offset) stopWords.add(entry.key());
			}
			writer.close();
			/* load old stopWords file */
			String[] words = Files.readAllLines(stopWordsFileName, StandardCharsets.UTF_8);
			for(String word : words) {
				if(!word.isEmpty()) {
					stemmer.setCurrent(word.toLowerCase());
					if(stemmer.stem()) word = stemmer.getCurrent();
					if(!stopWords.contains(word)) stopWords.add(word);
				}
			}
        }catch(FileNotFoundException ex){
            ex.printStackTrace();
        }catch(IOException e){
        	e.printStackTrace();
        }
	}
	
	public void removeStopWords(String folder) {
		try {
			System.out.println("start remove stopWords...");
			File dir = new File(folder); 
			StringBuilder sb = new StringBuilder();
			for(File f:dir.listFiles()){ 
				/* detect project folder */
				if(f.isDirectory()) {
					String line = new String(Files.readAllBytes(new File(f, "re.prepro").getAbsolutePath()));
					String[] tokens = line.split(" ");
					for(String token : tokens) {
						if(token != null && token.length() > 0) {
							if(wordsMap.containsKey(token)) wordsMap.put(token, wordsMap.get(token) + 1);
							else wordsMap.put(token, 1);
						}
					}
					line = new String(Files.readAllBytes(new File(f, "code.prepro").getAbsolutePath()));
					tokens = line.split(" ");
					for(String token : tokens) {
						if(token != null && token.length() > 0) {
							if(wordsMap.containsKey(token)) wordsMap.put(token, wordsMap.get(token) + 1);
							else wordsMap.put(token, 1);
						}
					}
				}
			}
			System.out.println("Finish remove stopWords !! ");
		} catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	// sort HashMap by value
	private Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap) {
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
		if(args.length != 1) {
			System.out.println("input command : java PreprocessForWord2Vec folder");
			System.exit(0);
		}
		PreprocessForWord2Vec preprocess = new PreprocessForWord2Vec();
		preprocess.parseProjects(args[0]);
		System.out.println("Done ");
	}
}

