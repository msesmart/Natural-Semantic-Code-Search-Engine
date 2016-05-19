/**
 *  Author: 	Chengjun Yuan
 *				<cy3yb@virginia.edu>
 *	Time:		Jan. 2016
 *	Purpose:	extract words from readme files and code files of a number of java projects.
 *	HowToRun:	compile:	javac -cp '.:libstemmer.jar' PreprocessForWord2Vec.java
				run:		java -cp '.:libstemmer.jar' PreprocessForWord2Vec projectsFolder
 *  Version:	Feb.15 2016	change the algorithm to filter the stopwords and remove the words whose length exceed ten.
				Feb.21 2016 add 'ctags' to parse the project code files, which supports many kinds of languages.
				Feb.24 2016 remove the consideration of commit messages. Add the consideration of description of projects.
				Feb.27 2016 add function to visit the github api and retrieve the description.
				April.11 2016 add function to evaluate the word2vector models.
 */
 
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.StandardCopyOption.*;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

public class PreprocessForWord2Vec {
	int numLoadedFiles = 0;
	int maxNumLoadedFiles = 2000;
	int numProjects = 0;
	int upperLimitNumOfSingleWord = 3;  // the upper limit number of single word in the readme and code for each project.
	Double[] termsFrequencyWeight = {2.0, 2.0, 2.0, 2.0};
	StringBuilder descriptionSb, codeSb, readMeSb, commitSb;
	String projectsDirectory;
	SnowballStemmer stemmer;
	HashMap<String, Integer> wordsMap;
	HashMap<String, Integer> wordsDocFreq;
	ArrayList<HashMap<String, Integer>> wordsDocFreqByGroup;
	HashMap<String, Double> wordsTermFreq;
	HashSet<String> stopWords;
	ArrayList<HashSet<String>> stopWordsByGroup;
	HashSet<String> initialStopWords;
	HashSet<String> omittedProjects;
	HashMap<String, Integer> projectsNo; // each project has its unique No.  projectName -> No.
	String[] noProjects; // No. -> projectName
	Boolean includeWikiForWord2VecModel, includeMoreDescriptionsForWord2VecModel;
	String wikiFile = "enwiki.sentences";
	int wikiSentencesNum = 200000;
	String moreDescriptionsFile = "projDesc.csv"; // include descriptions of many projects from Github 
 	Boolean newParseProject = false;

	public PreprocessForWord2Vec(){
		/*try{
			reviewsWriter = new PrintWriter(new BufferedWriter(new FileWriter(reviewsFile, true)));
		}catch (IOException e) {
			e.printStackTrace();
		}*/
		descriptionSb = new StringBuilder();
		codeSb = new StringBuilder();
		readMeSb = new StringBuilder();
		commitSb = new StringBuilder();
		stemmer = new englishStemmer();
		wordsMap = new HashMap<String, Integer>();
		wordsDocFreq = new HashMap<String, Integer>();
		wordsDocFreqByGroup = new ArrayList<HashMap<String, Integer>>();
		stopWordsByGroup = new ArrayList<HashSet<String>>();
		for(int i = 0; i < 4; i++) {
			HashMap<String, Integer> newMap = new HashMap<String, Integer>();
			wordsDocFreqByGroup.add(newMap);
			HashSet<String> newSet = new HashSet<String>();
			stopWordsByGroup.add(newSet);
		}
		wordsTermFreq = new HashMap<String, Double>();
		stopWords = new HashSet<String>();
		initialStopWords = new HashSet<String>();
		omittedProjects = new HashSet<String>();
		projectsNo =  new HashMap<String, Integer>();
		includeWikiForWord2VecModel = true;
		includeMoreDescriptionsForWord2VecModel = true; 
	}
	
	public void numOfProjects(String folder) {
		try{
			projectsDirectory = folder;
			File dir = new File(projectsDirectory);
			File[] projectDirs = dir.listFiles();
			numProjects = 0; 
			for(File f : dir.listFiles()) {
				if(f.isDirectory()) numProjects++;
			}
			noProjects = new String[numProjects];
			
			File projectsNoFile = new File(dir, "projectsNo.HashMap");
			if(projectsNoFile.exists()) projectsNoFile.delete();

			System.out.println("generate projectsNoFile ...");
			for(int i = 0; i < numProjects; i ++) {
				noProjects[i] = projectDirs[i].getName();
				projectsNo.put(noProjects[i], i + 1);
			}
			saveObjectToFile(folder, "projectsNo.HashMap", projectsNo);
			
			PrintWriter writer = new PrintWriter("projectsNameList.txt", "UTF-8");
			for(int i = 0; i < numProjects; i++) {
				writer.println(noProjects[i]);
			}
			writer.close();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void dumpGitLog(String topFolder){
		File dir = new File(topFolder);
		File[] allFiles = dir.listFiles();
		ArrayList<File> allDirs = new ArrayList<File>();
		for (File file : allFiles) {
			if (file.isDirectory()) {
				allDirs.add(file.getAbsoluteFile());
			}
		}
		int i = 0;
		for (File folder : allDirs) {
			try {
				i++;
				System.out.println(String.valueOf(i) + folder.getAbsolutePath());
				String[] cmd = new String[]{"/bin/bash", "-c", "git log --date=iso --no-merges > change_log.txt"};
				ProcessBuilder pb = new ProcessBuilder().command(cmd).directory(folder);
				Process p = pb.start();
				int exit = p.waitFor();
				if(exit != 0){
					System.out.println("Not normal process **********");
					//System.exit(exit);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void ctagsProjects(String directory) {
		File dir = new File(directory);
		File[] allFiles = dir.listFiles();
		ArrayList<File> allDirs = new ArrayList<File>();
		for (File file : allFiles) {
			if (file.isDirectory()) {
				allDirs.add(file.getAbsoluteFile());
			}
		}
		int i = 0;
		for (File folder : allDirs) {
			try {
				i++;
				System.out.println("ctags " + String.valueOf(i) + folder.getAbsolutePath());
				String[] cmd = new String[]{"/bin/bash", "-c", "ctags -R -n"};
				ProcessBuilder pb = new ProcessBuilder().command(cmd).directory(folder);
				Process p = pb.start();
				int exit = p.waitFor();
				if(exit != 0){
					System.out.println("Not normal process **********");
					//System.exit(exit);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public Boolean deleteDirectory(File directory) {
		if(directory.exists()){
	    	File[] files = directory.listFiles();
	        if(null != files){
	        	for(int i = 0; i < files.length; i++) {
	        		if(files[i].isDirectory()) {
	        			deleteDirectory(files[i]);
	        		} else {
	        			files[i].delete();
	        		}
	        	}
	        }
		}
		return(directory.delete());
	}
	
	public void removeProjectsWithNoReadme(String folder) {
		File dir = new File(folder);
		Boolean flag = false;
		for(File f : dir.listFiles()){
			flag = false;
			if(f.isDirectory()) {
				for(File subFile : f.listFiles()) {
					if(subFile.isFile() && subFile.getName().toLowerCase().startsWith("readme")) {
						flag = true;
						break;
					}
				}
			}
			if(!flag) {
			  System.out.println("No ReadMe file. Remove this project");
			  System.out.println("   .." + f.getName());
			  deleteDirectory(f);
			} else {
			  System.out.print("Yes ReadMe file.  ");
			  System.out.println("   .." + f.getName());
			}
		}
	}
	
	public double getFileFolderSize(File dir, double upLimitProjectSize) {
		double size = 0.0;
		if (dir.isDirectory() && !dir.isHidden() && dir.list() != null && dir.listFiles().length != 0) {
			for (File file : dir.listFiles()) {
				if (file.isFile()) {
					size += (double)file.length() / 1024 / 1024;
					if(size > upLimitProjectSize) break;
				} else {
					size += getFileFolderSize(file, upLimitProjectSize);
					if(size > upLimitProjectSize) break;
				}
			}
		} else if (dir.isFile()) {
			size += (double)dir.length() / 1024 / 1024;
		} else ;
		return size;
	}
	
	public void removeBigProjects(final double upLimitProjectSize, String folder) {
			File dir = new File(folder);
			int threads = Runtime.getRuntime().availableProcessors();
		    ExecutorService service = Executors.newFixedThreadPool(threads);
			
		    int i = 1;
		    //List<Future<Output>> futures = new ArrayList<Future<Output>>();
		    for(final File subDir : dir.listFiles()) {
		    	final int index = i; i++;
		        Callable<Integer> callable = new Callable<Integer>() {
		            public Integer call() throws Exception {
		            	double size = getFileFolderSize(subDir, upLimitProjectSize);
		    			if(size > upLimitProjectSize) {
		    				System.out.printf("Big file size = %f. Remove this project \n", size);
		    				System.out.println("   .." + subDir.getName());
		    				deleteDirectory(subDir);
		    			} else {
		    				System.out.print(String.valueOf(index) + " ");
		    				System.out.print("Yes Moderate file.  ");
		    				System.out.println("   .." + subDir.getName());
		    			}
		                return 1;
		            }
		        };
		        //futures.add(service.submit(callable));
		        service.submit(callable);
		        //System.out.print(String.valueOf(i) + " "); i++;
		    }
		    service.shutdown();
	}
	
	public String retrieveDescriptionFromUrl(String repositoryUrl) {
	  try{
	    /* for example of curl command*/
	    // curl 'https://api.github.com/repos/defunkt/jquery-pjax?client_id=1ec30d10a88b58b195d3&client_secret=6a022b4f38783acef7a777f22c7056710c1563e8'
	    String client_id = "1ec30d10a88b58b195d3";
	    String client_secret = "6a022b4f38783acef7a777f22c7056710c1563e8";
	    String commandLine = "curl '" + "https://api.github.com/repos/" + repositoryUrl + "?client_id=" + client_id + "&client_secret=" + client_secret + "'";
	    //System.out.println(commandLine);
	    String[] cmd = new String[]{"/bin/bash", "-c", commandLine};
			ProcessBuilder pb = new ProcessBuilder().command(cmd);
			Process p = pb.start();
			int exit = p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		  if(exit != 0){
			  System.out.println("Not normal process **********");
				//System.exit(exit);
			}
			
			/*
  	  URL gitApiUrl = new URL("https://api.github.com/repos/" + repositoryUrl);
  	  //BufferedReader readUrl = new BufferedReader(new InputStreamReader(gitApiUrl.openStream()));
  	  URLConnection conn = gitApiUrl.openConnection();
      conn.addRequestProperty("User-Agent", "msesmart");
      BufferedReader readUrl = new BufferedReader(new InputStreamReader(conn.getInputStream())); */
			String line, description;
			while((line = reader.readLine()) != null) {
				line = line.trim();
				//System.out.println(line);
				int index = line.indexOf("\"description\"");
				if(index >= 0 && line.length() > index + 16) {
					int index2 = line.indexOf("\"", index + 16);
					if(index2 >= 0)
						description = line.substring(index + 16, index2);
					else
						description = line.substring(index + 16);
					System.out.println("description: " + description);
					return description;
				}
			}
			return "";
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return "";
		} catch(IOException e){
			e.printStackTrace();
			return "";
		} catch (InterruptedException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	public Boolean descriptionExist(String projectDirectory) {
	  File file = new File(projectDirectory, "description.prepro");
			if(!file.exists()) return false;
			else return true;
	}
	
	public String getDescriptionFromGithub(String projectDirectory) {
	  //if(descriptionExist(projectDirectory)) return "";
	  File projectDir = new File(projectDirectory);
	  try {
			String[] cmd = new String[]{"/bin/bash", "-c", "git config --get remote.origin.url"};
			ProcessBuilder pb = new ProcessBuilder().command(cmd).directory(projectDir);
			Process p = pb.start();
			int exit = p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			if(exit != 0){
			  System.out.println("Not normal process **********");
				//System.exit(exit);
			}
			String repositoryUrl = reader.readLine().trim(); // for example: git@github.com:scy/dotscy.git
			System.out.println(" " + repositoryUrl + " ");
			if(repositoryUrl != null && repositoryUrl.length() > 14 && repositoryUrl.indexOf(":") >= 0) {
				if(repositoryUrl.startsWith("https")) {
					return retrieveDescriptionFromUrl(repositoryUrl.substring(repositoryUrl.indexOf("com") + 4));
				} else if(repositoryUrl.startsWith("git") && repositoryUrl.indexOf(".git") >= 0) {
					return retrieveDescriptionFromUrl(repositoryUrl.substring(repositoryUrl.indexOf(":") + 1, repositoryUrl.indexOf(".git")));
				}
			}
			return "";
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		} catch (InterruptedException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	// download project descriptions from github API
	public void downloadDescriptions(String folder) {
		try{
			File dir = new File(folder);
			long limitedRate = 1450;
			long startTime = System.currentTimeMillis();
			int i = 1;
			for(File f : dir.listFiles()){				
				if(f.isDirectory()) {
					System.out.print("project #"+ String.valueOf(i) + " " + f.getName());
					descriptionSb.setLength(0);
					if(descriptionExist(f.getAbsolutePath())) {
						System.out.println(" description exist");
					} else {
						long endTime = System.currentTimeMillis();
						if(endTime - startTime < limitedRate) Thread.sleep(limitedRate - (endTime - startTime));
						descriptionSb.append(getDescriptionFromGithub(f.getAbsolutePath()));
						startTime = System.currentTimeMillis();
						if(descriptionSb.length() > 0) {
							System.out.println(" description found and saved");
							saveStringIntoFileUnderFolder(f.getAbsolutePath(), "description.prepro", descriptionSb.toString());
						} else {
							System.out.println(" No description");
						}
					}
				} else System.out.println("project #"+ String.valueOf(i));
				i ++;
			}
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void removeProjectsWithNoDescription(String folder) {
		try{
			File dir = new File(folder);
			int n = 0; long startTime, endTime, limitedRate = 1500;
			startTime = System.currentTimeMillis();
			for(File f : dir.listFiles()){
				if(f.isDirectory()) {
				  File file = new File(f, "description.prepro");
					if(!file.exists()) {
						endTime = System.currentTimeMillis();
						if(endTime - startTime < limitedRate) Thread.sleep(limitedRate - (endTime - startTime));
						String line = getDescriptionFromGithub(f.getAbsolutePath());
						if(line.trim().length() > 0) {
							saveStringIntoFileUnderFolder(f.getAbsolutePath(), "description.prepro", line);
							System.out.println(f.getName() + "  description: " + line);
						} else {
							n ++;
							System.out.println("No description. Delete project: " + f.getName() + "  " + String.valueOf(n));
							deleteDirectory(f);
						}
						startTime = System.currentTimeMillis();
					}
				}
			}
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void removeProjectsWithNoDescriptionWithNoCheck(String folder) {
		
			File dir = new File(folder);
			int n = 0;
			for(File f : dir.listFiles()){
				if(f.isDirectory()) {
					File file = new File(f, "description.prepro");
					if(!file.exists()) {
						n++;
						System.out.println("No description. Delete project: " + f.getName() + "  " + String.valueOf(n));
						deleteDirectory(f);
					} else {
						file = new File(f, "description.prepro_stem");
						if(!file.exists()) {
							n++;
							System.out.println("No description. Delete project: " + f.getName() + "  " + String.valueOf(n));
							deleteDirectory(f);
						}
					}
				}
			}
	
	}

	public void removeProjectsWithNoCodeWithNoCheck(String folder) {

			File dir = new File(folder);
			int n = 0;
			for(File f : dir.listFiles()){
				if(f.isDirectory()) {
					File file = new File(f, "code.prepro");
					if(!file.exists()) {
						n++;
						System.out.println("No code. Delete project: " + f.getName() + "  " + String.valueOf(n));
						deleteDirectory(f);
					} else {
						file = new File(f, "code.prepro_stem");
						if(!file.exists()) {
							n++;
							System.out.println("No code. Delete project: " + f.getName() + "  " + String.valueOf(n));
							deleteDirectory(f);
						}
					}
				}
			}
	}
	
	public void filterOmittedProjects(String folder, double upLimitProjectSize) {
		omittedProjects.clear();
		File dir = new File(folder);
		// filter project with no readme file
		Boolean flag = false;
		for(File f : dir.listFiles()){
			flag = false;
			if(f.isDirectory()) {
				for(File subFile : f.listFiles()) {
					if(subFile.isFile() && subFile.getName().toLowerCase().startsWith("readme")) {
						flag = true;
						break;
					}
				}
			}
			if(!flag) { // No readme file
				omittedProjects.add(f.getName());
			}
		}
		// filter big project with size more than upLimitProjectSize
		// for(File f : dir.listFiles()){
			// if(f.isDirectory() && !omittedProjects.contains(f.getName())) {
				// double size = getFileFolderSize(f, upLimitProjectSize);
		    	// if(size > upLimitProjectSize) {
		    		// omittedProjects.add(f.getName());
		    	// } 
			// }
		// }
		
		// filter projects with no description 
		try{
			int n = 0; long startTime, endTime, limitedRate = 1500;
			startTime = System.currentTimeMillis();
			for(File f : dir.listFiles()){
				if(f.isDirectory() && !omittedProjects.contains(f.getName())) {
				  File file = new File(f, "description.prepro");
					if(!file.exists()) {
						endTime = System.currentTimeMillis();
						if(endTime - startTime < limitedRate) Thread.sleep(limitedRate - (endTime - startTime));
						String line = getDescriptionFromGithub(f.getAbsolutePath());
						if(line.trim().length() > 0) {
							saveStringIntoFileUnderFolder(f.getAbsolutePath(), "description.prepro", line);
							System.out.println(f.getName() + "  description: " + line);
						} else {
							n ++;
							System.out.println("No description. " + f.getName() + "  " + String.valueOf(n));
							omittedProjects.add(f.getName());
						}
						startTime = System.currentTimeMillis();
					}
				}
			}
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
		// save omittedProjects to file
		saveObjectToFile(null, "omittedProjects.HashSet", omittedProjects);
		System.out.println("omittedProjects is saved in omittedProjects.HashSet");
	}
	
	public String loadDescriptionFile(String fileName) {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String str;
			while ((str = in.readLine()) != null){
				str = tokenizerNormalization(str);
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
	
	public String LoadReadMeFile(String fileName) {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String str;
			while ((str = in.readLine()) != null){
				str = tokenizerNormalization(str);
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
			if(Character.isUpperCase(text.charAt(i)) && i != 0) {
				// consider the continuing Upper case Words like "SSN".
				if(Character.isLowerCase(text.charAt(i - 1))) { 
					String token = text.substring(start, i).toLowerCase();
					//stemmer.setCurrent(token);
					//if(stemmer.stem()) token = stemmer.getCurrent();
					sb.append(token); sb.append(" ");
					start = i;
				}
			}
		}
		String token = text.substring(start, i).toLowerCase();
		//stemmer.setCurrent(token);
		//if(stemmer.stem()) token = stemmer.getCurrent();
		sb.append(token); sb.append(" ");
		//System.out.println(text + " PCW " + sb.toString());
		return sb.toString();
	}
	
	public String parseImportedPackages(String text) {
		String[] tokens = text.split("[^a-zA-Z']+");
		StringBuilder sb = new StringBuilder();
		for(String token : tokens) {
			if(token.equals(token.toLowerCase()) || token.equals(token.toUpperCase())) {
				//stemmer.setCurrent(token.toLowerCase());
				//if(stemmer.stem()) token = stemmer.getCurrent();
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
				//stemmer.setCurrent(token.toLowerCase());
				//if(stemmer.stem()) token = stemmer.getCurrent();
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
				//}else if(str.startsWith("public") || str.startsWith("private") || str.startsWith("class") || str.startsWith("void")) {
				}
				/*else if(str.startsWith("public class")) {
					sb.append(parseClassMethods(str));
				} else ; */
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
	
	/* read commit message from commit file - change_log.txt */
	public String loadCommitFile(String fileName){
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
			StringBuilder sb = new StringBuilder(); 
			String line, author, date, emailAddress, message;
			String modifiedFileName; String commitID; String indexID; String patchID;
			line = reader.readLine();
			while(line != null){
				if(line.startsWith("commit")){
					commitID=new String(line.substring(7));
					line = reader.readLine();
					author = new String(line.substring(line.indexOf(" ")+1,line.lastIndexOf(" ")));
					emailAddress = new String(line.substring(line.lastIndexOf(" ")+1));
					line = reader.readLine();
					date = new String(line.substring(8));
							//sb.setLength(0);
					while((line = reader.readLine()) != null && (!line.startsWith("commit"))){
						sb.append(line); sb.append(" ");
					}
				} else line = reader.readLine();
			}
			reader.close();
			message = tokenizerNormalization(sb.toString());
			return message;
		}catch(IOException e){
			System.err.format("[Error]Failed to open file %s ", fileName);
			e.printStackTrace();
			return null;
		}
	}
	
	/* stemmer the words in the string sentence. */
	public String stringStemmer(String text) {
		if(text == null || text.length() == 0) return text;
		SnowballStemmer stemmer_ = new englishStemmer();
		String[] tokens = text.split(" ", -1);
		StringBuilder sb = new StringBuilder();
		for(String token : tokens) {
			//if(token.matches(".*\\d+.*")) token = "";
			if(!token.isEmpty() && !token.equals(" ")){
				token = token.toLowerCase();
				stemmer_.setCurrent(token);
				if(stemmer_.stem()) token = stemmer_.getCurrent();
				sb.append(token);
				sb.append(" ");
			}
		}
		return sb.toString();
	}
	
	// recursively parse code files in a directory
	public String parseAndStemSingleProject(String folder, String prefix, String suffix) {
		File projectDir = new File(folder);
		StringBuilder descriptionSb_ = new StringBuilder();
		StringBuilder readMeSb_ = new StringBuilder();
		StringBuilder codeSb_ = new StringBuilder();
		HashSet<File> set = new HashSet<File>();
		Queue<File> queue = new LinkedList<File>();
		queue.offer(projectDir); set.add(projectDir); int i = 0;
		double folderSize = 0.0; double folderSizeThreshold = 30.0;
		while(!queue.isEmpty() && folderSize < folderSizeThreshold) {
			File dir = queue.poll(); i++;
			//System.out.print(String.valueOf(i) + " ");
			if(dir.list() == null || dir.listFiles().length == 0) continue;
			for(File f : dir.listFiles()){
				if(f != null && f.isFile() && !f.isHidden()) {
					String fileName = f.getName().toLowerCase();
					if(i == 1 && fileName.startsWith(prefix) && fileName.indexOf(".prepro") < 0 && fileName.indexOf("_stem") < 0 && fileName.indexOf("_rs") < 0) {
						//System.out.println(numLoadedFiles + " load README file"+" : " + f.getName());
						readMeSb_.append(LoadReadMeFile(f.getAbsolutePath()));
						numLoadedFiles ++;
						//folderSize += f.length() / 1024.0 / 1024.0;
					} else if(fileName.endsWith(suffix)) {
						//System.out.println(numLoadedFiles + " load code file"+" : "+f.getName());
						codeSb_.append(loadCodeFile(f.getAbsolutePath()));
						numLoadedFiles ++;
						folderSize += f.length() / 1024.0 / 1024.0;
					} else if(i == 1 && fileName.equals("description.prepro") && fileName.indexOf("_stem") < 0 && fileName.indexOf("_rs") < 0) {
						descriptionSb_.append(loadDescriptionFile(f.getAbsolutePath()));
						numLoadedFiles ++;
					}
				}
				else if(f != null && f.isDirectory() && !f.isHidden()) {
					if(!set.contains(f)) {
						queue.offer(f);
						set.add(f);
					}
				}
				if(folderSize > folderSizeThreshold) break;
			}
		}
		System.out.println(" size = " + String.valueOf(folderSize));
		
		if(readMeSb_.length() > 0)
			saveStringIntoFileUnderFolder(folder, "re.prepro", readMeSb_.toString());
		if(codeSb_.length() > 0)
			saveStringIntoFileUnderFolder(folder, "code.prepro", codeSb_.toString());
		
		String description_ = stringStemmer(descriptionSb_.toString());
		String readMe_ = stringStemmer(readMeSb_.toString());
		String code_ = stringStemmer(codeSb_.toString());
		if(description_.length() > 0) saveStringIntoFileUnderFolder(folder, "description.prepro_stem", description_);
		if(readMe_.length() > 0) saveStringIntoFileUnderFolder(folder, "re.prepro_stem", readMe_);
		if(code_.length() > 0) saveStringIntoFileUnderFolder(folder, "code.prepro_stem", code_);
		return projectDir.getName();
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
	
	// check whether this project has been parsed before.
	public boolean hasBeenParsed(File folder) {
		String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
		for(String preproFile : preproFiles) {
			File file = new File(folder, preproFile);
			if(!file.exists()) return false;
		}
		//return true;
		return false;
	}
	
	/* parse the code file of each project for the class & function name
		stemmer the code, description and readme file, save them to .prepro_stem
	 */
	public void parseAndStemProjects(String folder) {
	    File dir = new File(folder);
		int threads = Runtime.getRuntime().availableProcessors();
		System.out.println("number of threads: " + String.valueOf(threads));
		ExecutorService service = Executors.newFixedThreadPool(threads / 2);
		
  		int i = 1;
  		for(final File f : dir.listFiles()){
  			final int index = i; i++;
		    Callable<Integer> callable = new Callable<Integer>() {
		        public Integer call() throws Exception {
		            //
		            if(f.isDirectory()) {
						//System.out.println("project #"+ String.valueOf(index) + f.getAbsolutePath());
						//String str = parseAndStemSingleProject(f.getAbsolutePath(), "readme", "java", readMeSb_, codeSb_, descriptionSb_);
						String str = parseAndStemSingleProject(f.getAbsolutePath(), "readme", "java");
						System.out.println("project parsed #"+ String.valueOf(index) + " " + str);
					} else {
						System.out.println("project code file existed #"+ String.valueOf(index) + " " + f.getName());
					}
		            return 1;
				}
		    };
		    service.submit(callable);
  		}
		service.shutdown();
	}
	
	public void parseAndStemProjectsName(String folder) {
		File dir = new File(folder);
		for(File f : dir.listFiles()){
			if(f.isDirectory()) {
				String projectName = f.getName();
				projectName = parseImportedPackages(projectName);
				projectName = stringStemmer(projectName);
				saveStringIntoFileUnderFolder(f.getAbsolutePath(), "projectName.prepro_stem", projectName);
			}
		}
		System.out.println("Finished parse And Stem ProjectsName.. ");
	}

	public void stemProjects(String folder) {
		try {
			File dir = new File(folder);
			String[] preproFiles = {"description.prepro", "re.prepro", "code.prepro"};
			for(File f : dir.listFiles()){
				if(f.isDirectory()) {
					for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
							File preproStemFile = new File(f, preproFile + "_stem");
							if(preproStemFile.exists()) preproStemFile.delete();
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							line = line.replaceAll("\n", " ").trim();
							if(line.length() > 0) {
								String stemLine = stringStemmer(line);
								saveStringIntoFileUnderFolder(f.getAbsolutePath(), preproFile + "_stem", stemLine);
							}
						}
					}
				}
			}
			System.out.println("Finished stem projects.. ");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String tokenizerNormalization(String text){
		if(text == null || text.length() == 0)return "";
		StringBuilder sb = new StringBuilder();
		//text = text.replaceAll("[^a-zA-Z\\s]", " ");
		text = text.replaceAll("[^a-zA-Z]", " ");
		String[] tokens = text.split(" ", -1);
		//String[] tokens = text.split("[^a-zA-Z']+");
		
		for(String token : tokens) {
			//token=token.replaceAll("\\W+", "");
			//token = token.replaceAll("\\p{Punct}+","");
			
			//if(token.matches(".*\\d+.*")) token = "";
			if(!token.isEmpty()){
				token = token.toLowerCase();
				//stemmer.setCurrent(token);
				//if(stemmer.stem()) token = stemmer.getCurrent();
				sb.append(token);
				sb.append(" ");
			}
		}
		return sb.toString();
	}
	
	public void saveObjectToFile(String folder, String fileName, Object obj){
		try{
			System.out.println("Save Object " + fileName + " in folder: " + folder);
			File fileDirectory = new File(folder, fileName);
			FileOutputStream fos =new FileOutputStream(fileDirectory);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
            oos.close();
			fos.close();
		}catch(IOException e){
            System.err.format("[Error]Failed to open or create file ");
            e.printStackTrace();
        }
	}

	public void removeTabSpace(String folder) {
		try {
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			System.out.println("start remove Tap Space...");
			File dir = new File(folder);
			StringBuilder sb = new StringBuilder();
			
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
							sb.setLength(0);
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							line = line.replaceAll("\t", " ");
							String[] tokens = line.split(" ");
							for(String token : tokens) {
								if(!token.isEmpty() && !token.equals(" ")) {
									sb.append(token + " ");
								}
							}
							saveStringIntoFileUnderFolder(f.getAbsolutePath(), preproFile, sb.toString());
						}
					}
				}
			}
			System.out.println("Finish remove Tap Space !! ");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// consider 12Million projects' descriptions for words doc frequency.
	public void wordsStaticsByGroupMore(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			System.out.println("start more words statics...");
			File dir = new File(folder);
			int projectsCount = 0;
			HashMap<String, Integer> wordsDocFreq_uniform = new HashMap<String, Integer>();
			HashMap<String, Integer> wordsDocFreq_projectName = new HashMap<String, Integer>();
			HashMap<String, Integer> wordsDocFreq_description = new HashMap<String, Integer>();
			HashMap<String, Integer> wordsDocFreq_readme = new HashMap<String, Integer>();
			HashMap<String, Integer> wordsDocFreq_code = new HashMap<String, Integer>();
			HashSet<String> wordsSetInSingleProject = new HashSet<String>();
			HashSet<String> wordsSetInSingleFile = new HashSet<String>();
			FileInputStream fis = new FileInputStream("initialStopWords.HashSet"); 
			ObjectInputStream ois = new ObjectInputStream(fis);
			initialStopWords = (HashSet)ois.readObject(); ois.close(); fis.close();
			String line = null;
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					projectsCount ++;
					wordsSetInSingleProject.clear();
					//HashMap<String, Integer> projectWords = new HashMap<String, Integer>();
					//for(String preproFile : preproFiles) {
					for(int i = 0; i < preproFiles.length; i++) {
						if(new File(f, preproFiles[i]).exists()) {
							wordsSetInSingleFile.clear();
							line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFiles[i])));
							//if(preproFiles[i].startsWith("re.prepro")) writer.println(line);
							String[] tokens = line.split(" ");
							for(String token : tokens) {
								if(token != null && token.length() > 0 && token.length() < 11 && !initialStopWords.contains(token)) {
									if(!wordsSetInSingleProject.contains(token)) {
										wordsSetInSingleProject.add(token);
										if(!wordsDocFreq_uniform.containsKey(token)) wordsDocFreq_uniform.put(token, 1);
										else wordsDocFreq_uniform.put(token, wordsDocFreq_uniform.get(token) + 1);
									}
									if(!wordsSetInSingleFile.contains(token)) {
										wordsSetInSingleFile.add(token);
										if(i == 0) {
											if(!wordsDocFreq_projectName.containsKey(token)) wordsDocFreq_projectName.put(token, 1);
											else wordsDocFreq_projectName.put(token, wordsDocFreq_projectName.get(token) + 1);
										} else if(i == 1) {
											if(!wordsDocFreq_description.containsKey(token)) wordsDocFreq_description.put(token, 1);
											else wordsDocFreq_description.put(token, wordsDocFreq_description.get(token) + 1);
										} else if(i == 2) {
											if(!wordsDocFreq_readme.containsKey(token)) wordsDocFreq_readme.put(token, 1);
											else wordsDocFreq_readme.put(token, wordsDocFreq_readme.get(token) + 1);
										} else {
											if(!wordsDocFreq_code.containsKey(token)) wordsDocFreq_code.put(token, 1);
											else wordsDocFreq_code.put(token, wordsDocFreq_code.get(token) + 1);
										}
									}
								}
							}
						} else System.out.println(f.getName() + "  " + preproFiles[i] + " Not exists");
					}
					//saveObjectToFile(f.getAbsolutePath(), "wordsSetInSingleProject.HashMap", wordsSetInSingleProject);
				}
			}

			ArrayList<String> strList = new ArrayList<String>();
			int index = 0;
			
			/*
			System.out.println("-- Load More Descriptions ..");
			fis = new FileInputStream(moreDescriptionsFile); 
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			while((line = br.readLine()) != null) {
				if(line.split("\t", -1).length >= 4) {
					String description_ = line.split("\t", -1)[3].trim();
					index++;
					//System.out.println(" - " + String.valueOf(i) + description_);
					if(description_.length() > 0) {
						String stemmedDescription = stringStemmer(tokenizerNormalization(description_));
						if(stemmedDescription.split(" ", -1).length >= 5) {
							strList.add(stemmedDescription);
						}
						if(index % 100000 == 0) System.out.println("-- " + String.valueOf(index) + description_);
					}
				}
			}
			
			strList = removeStopWords(strList, "initialStopWords.HashSet");
			for(String str : strList) {
				String[] tokens = str.split(" ", -1);
				wordsSetInSingleFile.clear();
				for(String token : tokens) {
					if(token != null && token.length() > 0 && token.length() < 11) {
						if(!wordsSetInSingleFile.contains(token)) {
							wordsSetInSingleFile.add(token);
							if(!wordsDocFreq_uniform.containsKey(token)) wordsDocFreq_uniform.put(token, 1);
							else wordsDocFreq_uniform.put(token, wordsDocFreq_uniform.get(token) + 1);

							if(!wordsDocFreq_description.containsKey(token)) wordsDocFreq_description.put(token, 1);
							else wordsDocFreq_description.put(token, wordsDocFreq_description.get(token) + 1);
						}
					}
				}
				
			}
			strList.clear();
			*/
			
			wordsDocFreq_uniform = (HashMap)sortByComparator(wordsDocFreq_uniform);
			wordsDocFreq_projectName = (HashMap)sortByComparator(wordsDocFreq_projectName);
			wordsDocFreq_description = (HashMap)sortByComparator(wordsDocFreq_description);
			wordsDocFreq_readme = (HashMap)sortByComparator(wordsDocFreq_readme);
			wordsDocFreq_code = (HashMap)sortByComparator(wordsDocFreq_code);

			saveObjectToFile(null, "wordsDocFreq_uniform.HashMap", wordsDocFreq_uniform);
			saveObjectToFile(null, "wordsDocFreqInProjectName.HashMap", wordsDocFreq_projectName);
			saveObjectToFile(null, "wordsDocFreqInDescription.HashMap", wordsDocFreq_description);
			saveObjectToFile(null, "wordsDocFreqInReadme.HashMap", wordsDocFreq_readme);
			saveObjectToFile(null, "wordsDocFreqInCode.HashMap", wordsDocFreq_code);

			saveHashMapToFile_StringInteger("wordsDocFreq_uniform.txt", wordsDocFreq_uniform);
			saveHashMapToFile_StringInteger("wordsDocFreq_projectName.txt", wordsDocFreq_projectName);
			saveHashMapToFile_StringInteger("wordsDocFreq_description.txt", wordsDocFreq_description);
			saveHashMapToFile_StringInteger("wordsDocFreq_readme.txt", wordsDocFreq_readme);
			saveHashMapToFile_StringInteger("wordsDocFreq_code.txt", wordsDocFreq_code);

			System.out.println("-- Finish more words statics by group !! ");	
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace();
		}
	}
	
	public void wordsStatics(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			
			System.out.println("start words statics...");
			File dir = new File(folder);
			int projectsCount = 0;
			HashMap<String, Integer> wordsSetInSingleProject = new HashMap<String, Integer>();
			
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					projectsCount ++;
					wordsSetInSingleProject.clear();
					//HashMap<String, Integer> projectWords = new HashMap<String, Integer>();
					//for(String preproFile : preproFiles) {
					for(int i = 0; i < preproFiles.length; i++) {
						if(new File(f, preproFiles[i]).exists()) {
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFiles[i])));
							//if(preproFiles[i].startsWith("re.prepro")) writer.println(line);
							String[] tokens = line.split(" ");
							for(String token : tokens) {
								if(token != null && token.length() > 0 && token.length() < 10) {
									if(!wordsSetInSingleProject.containsKey(token)) {
										wordsSetInSingleProject.put(token, 1);
										if(!wordsDocFreq.containsKey(token)) wordsDocFreq.put(token, 1);
										else wordsDocFreq.put(token, wordsDocFreq.get(token) + 1);
										if(!wordsTermFreq.containsKey(token)) wordsTermFreq.put(token, termsFrequencyWeight[i]);
										else wordsTermFreq.put(token, wordsTermFreq.get(token)+ termsFrequencyWeight[i]);
									} else if(wordsSetInSingleProject.get(token) < upperLimitNumOfSingleWord) {
										wordsSetInSingleProject.put(token, wordsSetInSingleProject.get(token) + 1);
										if(!wordsTermFreq.containsKey(token)) wordsTermFreq.put(token, termsFrequencyWeight[i]);
										else wordsTermFreq.put(token, wordsTermFreq.get(token)+ termsFrequencyWeight[i]);
									}
									
								}
							}
						} else System.out.println(f.getName() + "  " + preproFiles[i] + " Not exists");
					}
					//saveObjectToFile(f.getAbsolutePath(), "wordsSetInSingleProject.HashMap", wordsSetInSingleProject);
				}
			}

			Map<String, Integer> sortedWordsMap = sortByComparator(wordsDocFreq);
			PrintWriter writer = new PrintWriter("wordsDocFreq.csv", "UTF-8");
			for(Map.Entry<String,Integer> entry : sortedWordsMap.entrySet()) {
				writer.println(entry.getKey() + ", " + String.valueOf(entry.getValue()));
			}
			writer.close();
			numProjects = projectsCount;
			saveObjectToFile(null, "wordsDocFreq.HashMap", wordsDocFreq);
			saveObjectToFile(null, "wordsTermFreq.HashMap", wordsTermFreq);
			System.out.println("Finish words statics !! ");
			
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void wordsStaticsByGroup(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			
			System.out.println("start words statics...");
			File dir = new File(folder);
			//HashMap<String, Integer> wordsSetInSingleProject = new HashMap<String, Integer>();
			HashSet<String> wordsInSingleGroup = new HashSet<String>();
			wordsDocFreqByGroup.clear();
			for(int i = 0; i < preproFiles.length; i++) {
				HashMap<String, Integer> groupMap = new HashMap<String, Integer>();
				for(File f : dir.listFiles()){
					/* detect project folder */
					if(f.isDirectory() && new File(f, preproFiles[i]).exists()) {
						wordsInSingleGroup.clear();
						String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFiles[i])));
						String[] tokens = line.split(" ");
						for(String token : tokens) {
							if(token != null && token.length() > 0 && token.length() < 11) {
								if(!wordsInSingleGroup.contains(token)) {
									wordsInSingleGroup.add(token);
									if(!groupMap.containsKey(token)) groupMap.put(token, 1);
									else groupMap.put(token, groupMap.get(token) + 1);
								}
							}
						} 
					} else System.out.println(f.getName() + "  " + preproFiles[i] + " Not exists");
				}
				wordsDocFreqByGroup.add(groupMap);
			}

			saveObjectToFile(null, "wordsDocFreqInProjectName.HashMap", wordsDocFreqByGroup.get(0));
			saveObjectToFile(null, "wordsDocFreqInDescription.HashMap", wordsDocFreqByGroup.get(1));
			saveObjectToFile(null, "wordsDocFreqInReadme.HashMap", wordsDocFreqByGroup.get(2));
			saveObjectToFile(null, "wordsDocFreqInCode.HashMap", wordsDocFreqByGroup.get(3));

			System.out.println("Finish words statics by Group !! ");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void generateStopWords(String stopWordsFileName, double offset) {
		try {
			/* read wordsDocFreq HashMap from file */
			System.out.println("read wordsDocFreq.HashMap ");
			FileInputStream fis = new FileInputStream("wordsDocFreq.HashMap"); ObjectInputStream ois = new ObjectInputStream(fis);
			wordsDocFreq = (HashMap)ois.readObject(); ois.close(); fis.close();
			Map<String, Integer> sortedWordsMap = sortByComparator(wordsDocFreq); // sort the wordsMap based on value
			/* write wordsStatics into file */
			int offNum = (int)(offset * numProjects);
			System.out.printf("offNum for generate StopWords is %d \n", offNum);
			PrintWriter writer = new PrintWriter("wordsDocFrequencyStatics.txt", "UTF-8");
			for(String keyToken : sortedWordsMap.keySet()) {
				writer.println(keyToken + "," + String.valueOf(sortedWordsMap.get(keyToken)));
				if(sortedWordsMap.get(keyToken) >= offNum) stopWords.add(keyToken);
			}
			writer.close();
			/* load old stopWords file */
			List<String> words = Files.readAllLines(Paths.get(stopWordsFileName), Charset.defaultCharset());
			for(String word : words) {
				if(!word.isEmpty()) {
					stemmer.setCurrent(word.toLowerCase());
					if(stemmer.stem()) word = stemmer.getCurrent();
					initialStopWords.add(word);
					if(!stopWords.contains(word)) stopWords.add(word);
				}
			}
			/* save initalStopWords*/
			saveObjectToFile(null, "initialStopWords.HashSet", initialStopWords);
			/* save new stopWords file */
			saveObjectToFile(null, "updatedEnglishStopWords.HashSet", stopWords);
			writer = new PrintWriter("updatedEnglish.stop", "UTF-8");
			for(String keyToken : stopWords) {
				writer.println(keyToken);
			}
			writer.close();
			System.out.println("finished generateStopWords.. ");
		} catch(FileNotFoundException ex) {
			ex.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
		}
	}

	public void generateStopWordsByGroupMore(String stopWordsFileName, double offset) {
		try {
			/* read wordsDocFreq HashMap from file By Group*/
			System.out.println("- read wordsDocFreq HashMap from file By Group.. ");
			wordsDocFreqByGroup.clear(); 
			FileInputStream fis = new FileInputStream("wordsDocFreqInProjectName.HashMap"); ObjectInputStream ois = new ObjectInputStream(fis);
			HashMap<String, Integer> newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap); 
			System.out.println("size of wordsDocFreq_projectName is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInDescription.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInDescription is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInReadme.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInReadme is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInCode.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInCode is " + String.valueOf(newMap.size()));
			
			int offNum = (int)(offset * numProjects);
			System.out.printf("offNum for generate StopWords is %d \n", offNum);
			
			stopWordsByGroup.clear();
			for(int i = 0; i < wordsDocFreqByGroup.size(); i++) {
				newMap = wordsDocFreqByGroup.get(i);
				HashSet<String> newSet = new HashSet<String>();
				if(i == 0) { // for project name
					for(String keyToken : newMap.keySet()) {
						if(newMap.get(keyToken) <= 2) newSet.add(keyToken);
					}
				} else if(i == 1) { //  for description
					for(String keyToken : newMap.keySet()) {
						if(newMap.get(keyToken) <= 2 || newMap.get(keyToken) > offNum * 2) newSet.add(keyToken);
					}
				} else if(i == 2) { 
					for(String keyToken : newMap.keySet()) {
						if(newMap.get(keyToken) <= 2 || newMap.get(keyToken) > offNum) newSet.add(keyToken);
					}
				} else {
					for(String keyToken : newMap.keySet()) {
						if(newMap.get(keyToken) <= 2 || newMap.get(keyToken) > offNum / 2) newSet.add(keyToken);
					}
				}
 				
				stopWordsByGroup.add(newSet);
			}

			/* load old stopWords file */
			List<String> words = Files.readAllLines(Paths.get(stopWordsFileName), Charset.defaultCharset());
			for(String word : words) {
				if(!word.isEmpty()) {
					stemmer.setCurrent(word.toLowerCase());
					if(stemmer.stem()) word = stemmer.getCurrent();
					initialStopWords.add(word);
				}
			}
			/* save initalStopWords*/
			saveObjectToFile(null, "initialStopWords.HashSet", initialStopWords);
			/* save new stopWords file By Group*/
			saveObjectToFile(null, "updatedEnglishStopWordsInProjectName.HashSet", stopWordsByGroup.get(0));
			saveObjectToFile(null, "updatedEnglishStopWordsInDescription.HashSet", stopWordsByGroup.get(1));
			saveObjectToFile(null, "updatedEnglishStopWordsInReadme.HashSet", stopWordsByGroup.get(2));
			saveObjectToFile(null, "updatedEnglishStopWordsInCode.HashSet", stopWordsByGroup.get(3));
			
			System.out.println("-- finished generateStopWords By Group.. ");
		} catch(FileNotFoundException ex) {
			ex.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
		}
	}

	public void generateStopWordsByGroup(String stopWordsFileName, double offset) {
		try {
			/* read wordsDocFreq HashMap from file By Group*/
			System.out.println("- read wordsDocFreq HashMap from file By Group.. ");
			wordsDocFreqByGroup.clear(); 
			FileInputStream fis = new FileInputStream("wordsDocFreqInProjectName.HashMap"); ObjectInputStream ois = new ObjectInputStream(fis);
			HashMap<String, Integer> newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap); 
			System.out.println("size of wordsDocFreqInProjectName is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInDescription.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInDescription is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInReadme.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInReadme is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInCode.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInCode is " + String.valueOf(newMap.size()));
			
			int offNum = (int)(offset * numProjects);
			System.out.printf("offNum for generate StopWords is %d \n", offNum);
			
			stopWordsByGroup.clear();
			for(int i = 0; i < wordsDocFreqByGroup.size(); i++) {
				newMap = wordsDocFreqByGroup.get(i);
				HashSet<String> newSet = new HashSet<String>();
				for(String keyToken : newMap.keySet()) {
					if(newMap.get(keyToken) >= offNum) newSet.add(keyToken);
				}
				stopWordsByGroup.add(newSet);
			}

			/* load old stopWords file */
			List<String> words = Files.readAllLines(Paths.get(stopWordsFileName), Charset.defaultCharset());
			for(String word : words) {
				if(!word.isEmpty()) {
					stemmer.setCurrent(word.toLowerCase());
					if(stemmer.stem()) word = stemmer.getCurrent();
					initialStopWords.add(word);
				}
			}
			/* save initalStopWords*/
			saveObjectToFile(null, "initialStopWords.HashSet", initialStopWords);
			/* save new stopWords file By Group*/
			saveObjectToFile(null, "updatedEnglishStopWordsInProjectName.HashSet", stopWordsByGroup.get(0));
			saveObjectToFile(null, "updatedEnglishStopWordsInDescription.HashSet", stopWordsByGroup.get(1));
			saveObjectToFile(null, "updatedEnglishStopWordsInReadme.HashSet", stopWordsByGroup.get(2));
			saveObjectToFile(null, "updatedEnglishStopWordsInCode.HashSet", stopWordsByGroup.get(3));
			
			System.out.println("-- finished generateStopWords By Group.. ");
		} catch(FileNotFoundException ex) {
			ex.printStackTrace();
		} catch(IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
		}
	}
	
	public void removeStopWords(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			System.out.println("start remove stopWords...");
			File dir = new File(folder);
			StringBuilder sb = new StringBuilder();
			/* load updatedEnglish.stop file */
			List<String> words = Files.readAllLines(Paths.get("updatedEnglish.stop"), Charset.defaultCharset());
			stopWords.clear();
			for(String word : words) {
				if(!word.isEmpty()) {
					stopWords.add(word);
				}
			}
			/* load initialStopWords.HashSet */
			System.out.println("read initialStopWords.HashSet ");
			FileInputStream fis = new FileInputStream("initialStopWords.HashSet"); ObjectInputStream ois = new ObjectInputStream(fis);
			initialStopWords = (HashSet)ois.readObject(); ois.close(); fis.close();
			
			PrintWriter writer = new PrintWriter("projectWords_filtered.csv", "UTF-8");
			HashMap<String, Integer> singleProjectWords = new HashMap<String, Integer>();
			
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
							singleProjectWords.clear();
							sb.setLength(0);
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							String[] tokens = line.split(" ");
							if(preproFile.startsWith("description")) {
								/* for description.prepro */
								//line = TokenizerNormalizationStemming(line);
								for(String token : tokens) {
									if(!token.isEmpty() && token.length() < 10) {
										if(!initialStopWords.contains(token)) {
											sb.append(token + " ");
										}
									}
								}
							} else if(preproFile.startsWith("code")) {
								for(String token : tokens) {
									if(!token.isEmpty() && token.length() < 10) {
										if(!stopWords.contains(token) && !singleProjectWords.containsKey(token)) {
											sb.append(token + " ");
											singleProjectWords.put(token, 1);
										}
									}
								}
							} else {
								/* for re.prepro and code.prepro*/
								for(String token : tokens) {
									if(!token.isEmpty() && token.length() < 10) {
										if(!stopWords.contains(token) && (!singleProjectWords.containsKey(token) || singleProjectWords.get(token) < upperLimitNumOfSingleWord)) {
											sb.append(token + " ");
											if(singleProjectWords.containsKey(token)) singleProjectWords.put(token, 1 + singleProjectWords.get(token));
											else singleProjectWords.put(token, 1);
										}
									}
								}
							}
							saveStringIntoFileUnderFolder(f.getAbsolutePath(), preproFile + "_rs", sb.toString());
							writer.print(sb.toString() + ",");
						}
					}
					writer.println(" ");
				}
			}
			writer.close();
			System.out.println("Finish remove stopWords !! ");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
		}
	}

	public void removeStopWordsByGroup(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			System.out.println("start remove stopWords...");
			File dir = new File(folder);
			StringBuilder sb = new StringBuilder();
			/* load updatedEnglishStopWords.HashSet file by group*/
			stopWordsByGroup.clear(); 
			FileInputStream fis = new FileInputStream("updatedEnglishStopWordsInProjectName.HashSet"); ObjectInputStream ois = new ObjectInputStream(fis);
			HashSet<String> newSet = (HashSet)ois.readObject(); ois.close(); fis.close();
			stopWordsByGroup.add(newSet); 
			System.out.println("size of updatedEnglishStopWordsInProjectName is " + String.valueOf(newSet.size()));
			fis = new FileInputStream("updatedEnglishStopWordsInDescription.HashSet"); ois = new ObjectInputStream(fis);
			newSet = (HashSet)ois.readObject(); ois.close(); fis.close();
			stopWordsByGroup.add(newSet);
			System.out.println("size of updatedEnglishStopWordsInDescription is " + String.valueOf(newSet.size()));
			fis = new FileInputStream("updatedEnglishStopWordsInReadme.HashSet"); ois = new ObjectInputStream(fis);
			newSet = (HashSet)ois.readObject(); ois.close(); fis.close();
			stopWordsByGroup.add(newSet);
			System.out.println("size of updatedEnglishStopWordsInReadme is " + String.valueOf(newSet.size()));
			fis = new FileInputStream("updatedEnglishStopWordsInCode.HashSet"); ois = new ObjectInputStream(fis);
			newSet = (HashSet)ois.readObject(); ois.close(); fis.close();
			stopWordsByGroup.add(newSet);
			System.out.println("size of updatedEnglishStopWordsInCode is " + String.valueOf(newSet.size()));

			/* load initialStopWords.HashSet */
			System.out.println("read initialStopWords.HashSet ");
			fis = new FileInputStream("initialStopWords.HashSet"); ois = new ObjectInputStream(fis);
			initialStopWords = (HashSet)ois.readObject(); ois.close(); fis.close();
			
			HashMap<String, Integer> singleProjectWords = new HashMap<String, Integer>();
			
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					for(int i = 0 ; i < preproFiles.length; i++) {
						String preproFile = preproFiles[i];
						if(new File(f, preproFile).exists()) {
							HashSet<String> stopWordsSet = stopWordsByGroup.get(i);
							singleProjectWords.clear();
							sb.setLength(0);
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							String[] tokens = line.split(" ");

							if(preproFile.startsWith("code")) {
								for(String token : tokens) {
									if(!token.isEmpty() && token.length() < 11 && !initialStopWords.contains(token)) {
										/*if(!stopWordsSet.contains(token) && !singleProjectWords.containsKey(token)) {
											sb.append(token + " ");
											singleProjectWords.put(token, 1);
										}*/

										if(!stopWordsSet.contains(token) && (!singleProjectWords.containsKey(token) || singleProjectWords.get(token) < upperLimitNumOfSingleWord)) {
											sb.append(token + " ");
											if(singleProjectWords.containsKey(token)) singleProjectWords.put(token, 1 + singleProjectWords.get(token));
											else singleProjectWords.put(token, 1);
										}
									}
								}
							} else {
								/* for projectName, description & readme*/
								for(String token : tokens) {
									if(!token.isEmpty() && token.length() < 11 && !initialStopWords.contains(token)) {
										if(!stopWordsSet.contains(token) && (!singleProjectWords.containsKey(token) || singleProjectWords.get(token) < upperLimitNumOfSingleWord)) {
											sb.append(token + " ");
											if(singleProjectWords.containsKey(token)) singleProjectWords.put(token, 1 + singleProjectWords.get(token));
											else singleProjectWords.put(token, 1);
										}
									}
								}
							}
							saveStringIntoFileUnderFolder(f.getAbsolutePath(), preproFile + "_rs", sb.toString());
						}
					}
				}
			}
			System.out.println("-- Finish remove stopWords By Group !! ");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
		}
	}
	
	public String removeStopWords(String text, String stopWordsSetFileName) {
		try {
			System.out.format("read stopWords HashSet: %s %n", stopWordsSetFileName);
			FileInputStream fis = new FileInputStream(stopWordsSetFileName); ObjectInputStream ois = new ObjectInputStream(fis);
			HashSet<String> stopWordsSet = (HashSet)ois.readObject(); ois.close(); fis.close();
			String[] tokens = text.split(" ", -1);
			StringBuilder sb = new StringBuilder();
			for(String token: tokens) {
				if(token.length() > 0 && !stopWordsSet.contains(token)) {
					sb.append(token + " ");
				}
			}
			return sb.toString();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace(); return null;
		} catch (IOException e) {
			e.printStackTrace(); return null;
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return null;
		}
	}

	public ArrayList<String> removeStopWords(ArrayList<String> strList, String stopWordsSetFileName) {
		/* load updatedEnglish.stop file */
		try {
			System.out.format("read stopWords HashSet: %s %n", stopWordsSetFileName);
			FileInputStream fis = new FileInputStream(stopWordsSetFileName); ObjectInputStream ois = new ObjectInputStream(fis);
			HashSet<String> stopWordsSet = (HashSet)ois.readObject(); ois.close(); fis.close();

			StringBuilder sb = new StringBuilder();
			ArrayList<String> strRsList = new ArrayList<String>();
			for(int i = 0; i < strList.size(); i ++) {
				sb.setLength(0);
				String str = strList.get(i);
				String[] tokens = str.split(" ");
				for(String token : tokens) {
					if(!token.isEmpty() && !stopWordsSet.contains(token)) {
						sb.append(token); sb.append(" ");
					}
				}
				strRsList.add(sb.toString());
			}
			System.out.println("Finish remove ArrayList<String> stopWords !! ");
			return strRsList;
		} catch (FileNotFoundException ex) {
			ex.printStackTrace(); return null;
		} catch (IOException e) {
			e.printStackTrace(); return null;
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return null;
		}
	}


	public void generateDocForWord2Vec(String folder, String destFolder) {
		try {
			String[] preproFiles = {"description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			File dir = new File(folder);
			File[] projectDirs = dir.listFiles();
			File destDir = new File(destFolder);
			if(destDir.exists()) deleteDirectory(destDir);
			destDir.mkdir();
			StringBuilder sb = new StringBuilder(); 
			String line = null;

			PrintWriter writer = new PrintWriter("DocForWord2Vec.txt", "UTF-8");
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
				  sb.setLength(0);
				  for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
						  line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
						  sb.append(line); sb.append("\n");
						  writer.println(line);
						}
						//writer.print(",");
				  }
				  //writer.println(" ");
				  saveStringIntoFileUnderFolder(destDir.getAbsolutePath(), f.getName(), sb.toString());
				}
			}

			if(new File(dir, "additionalSentencesForWord2VecModel.txt").exists()) {
				FileInputStream fis = new FileInputStream("additionalSentencesForWord2VecModel.txt");
 				BufferedReader br = new BufferedReader(new InputStreamReader(fis));
				while ((line = br.readLine()) != null) {
					writer.println(line);
				}
				br.close();
			} else {
				PrintWriter writer2 = new PrintWriter("additionalSentencesForWord2VecModel.txt", "UTF-8");
				ArrayList<String> strList = new ArrayList<String>();
				int i = 0;

				if(includeMoreDescriptionsForWord2VecModel) {
					System.out.println("-- Load More Descriptions For Word2Vec Model..");
					FileInputStream fis = new FileInputStream(moreDescriptionsFile);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));
					while((line = br.readLine()) != null) {
						if(line.split("\t", -1).length >= 4) {
							String description_ = line.split("\t", -1)[3].trim();
							i++;
							//System.out.println(" - " + String.valueOf(i) + description_);
							if(description_.length() > 0) {
								String stemmedDescription = stringStemmer(tokenizerNormalization(description_));
								if(stemmedDescription.split(" ", -1).length >= 5) {
									strList.add(stemmedDescription);
								}
								if(i % 100000 == 0) System.out.println("-- " + String.valueOf(i) + description_);
							}
						}
					}
				}

				if(includeWikiForWord2VecModel) {
					System.out.format("-- Load Wiki data from %s For Word2Vec Model.. %n", wikiFile);
					FileInputStream fis = new FileInputStream(wikiFile);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));
					i = 0;
					while((line = br.readLine()) != null) {
						String stemmedDescription = stringStemmer(tokenizerNormalization(line));
						if(stemmedDescription.split(" ", -1).length >= 5) {
							strList.add(stemmedDescription);
						}
						i++; 
						if(i % 50000 == 0) {
							strList = removeStopWords(strList, "initialStopWords.HashSet");
							for(String str : strList) {
								if(str.split(" ", -1).length >= 5) {
									writer.println(str);
									writer2.println(str);
								}
							}
							strList.clear();
							System.out.format("-- read %d sentences from enwiki.. %n", i);
						}
						if(i >= wikiSentencesNum) break;
					}
				}

				strList = removeStopWords(strList, "initialStopWords.HashSet");
				for(String str : strList) {
					if(str.split(" ", -1).length >= 5) {
						writer.println(str);
						writer2.println(str);
					}
				}
				strList.clear();
				writer2.close();
			}
			writer.close();

			System.out.println("generateDocForWord2Vec finished !!");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void generateDocForWord2VecEvaluation() {
		try {
			ArrayList<String> strList = new ArrayList<String>();
			FileInputStream fis = new FileInputStream("twentyQueriesTopTenSimilarities");
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			String line = null; int i = 0;
			while((line = br.readLine()) != null) {
				if(i % 2 == 1) {
					String stemmedStr = stringStemmer(tokenizerNormalization(line));
					strList.add(stemmedStr);
				}
				i++;
			}
			strList = removeStopWords(strList, "updatedEnglishStopWords.HashSet");
			PrintWriter writer = new PrintWriter("docForWmd_Word2VecEvaluation.txt", "UTF-8"); 
			i = 1;
			for(String str : strList) {
				writer.println("\"" + String.valueOf(i++) + "\"" + "\t" + str);
			}
			writer.close();
			System.out.println("Finish generateDocForWord2Vec Evaluation .. ");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void generateDocsForLda(String folder, String destFolder) {
		try {
			String[] preproFiles = {"description.prepro_stem_rs", "re.prepro_stem_rs", "code.prepro_stem_rs"};
			File dir = new File(folder);
			File destDir = new File(destFolder);
			if(destDir.exists()) deleteDirectory(destDir);
			destDir.mkdir();
			StringBuilder sb = new StringBuilder(); String line;
			PrintWriter writer = new PrintWriter("description_readme.csv", "UTF-8");
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
				  sb.setLength(0);
				  for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
						  line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
						  sb.append(line);
						  writer.print(line);
						}
						writer.print(",");
				  }
				  writer.println(" ");
				  saveStringIntoFileUnderFolder(destDir.getAbsolutePath(), f.getName(), sb.toString());
				}
			}
			writer.close();
			System.out.println("generateDocsForLda finished !!");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void generateRankedWordsBasedOnTfidfForLdaWmd(String folder, int topNum) {
		try {
			// load wordsDocFreq.HashMap
			System.out.println("load wordsDocFreq.HashMap ");
			FileInputStream fis = new FileInputStream("wordsDocFreq.HashMap"); ObjectInputStream ois = new ObjectInputStream(fis);
			wordsDocFreq = (HashMap)ois.readObject(); ois.close(); fis.close();
			
			File dir = new File(folder);
			String[] preproFiles = {"projectName.prepro_stem_rs", "description.prepro_stem_rs", "re.prepro_stem_rs", "code.prepro_stem_rs"};
			PrintWriter writer = new PrintWriter("docForWmd.txt", "UTF-8");
			PrintWriter writerLda = new PrintWriter("docForLda.txt", "UTF-8");
			StringBuilder sbWmd = new StringBuilder();
			StringBuilder sbLda = new StringBuilder();
			HashMap<String, Double> wordsTfInSingleProject = new HashMap<String, Double>();
			HashMap<String, Double> wordsTfidfInSingleProject = new HashMap<String, Double>();
			HashMap<String, Integer> wordsFrequenceInSingleFileOfProject = new HashMap<String, Integer>();
			
			for(File f : dir.listFiles()){
				if(f.isDirectory()) {
					wordsTfInSingleProject.clear();
					wordsTfidfInSingleProject.clear();
					for(int i = 0; i < preproFiles.length; i++) {
						String preproFile = preproFiles[i];
						if(new File(f, preproFile).exists()) {
							wordsFrequenceInSingleFileOfProject.clear();
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							String[] tokens = line.split(" ");
							for(String token : tokens) {
								if(!token.isEmpty() && token.length() < 11) {
									if(!wordsTfInSingleProject.containsKey(token)) {
										wordsTfInSingleProject.put(token, termsFrequencyWeight[i]);
										wordsFrequenceInSingleFileOfProject.put(token, 1);
									} else {
										if(!wordsFrequenceInSingleFileOfProject.containsKey(token)) {
											wordsTfInSingleProject.put(token, wordsTfInSingleProject.get(token) + termsFrequencyWeight[i]);
											wordsFrequenceInSingleFileOfProject.put(token, 1);
										} else if (i < (preproFiles.length - 1) && wordsFrequenceInSingleFileOfProject.get(token) < upperLimitNumOfSingleWord) {
											wordsTfInSingleProject.put(token, wordsTfInSingleProject.get(token) + termsFrequencyWeight[i]);
											wordsFrequenceInSingleFileOfProject.put(token, wordsFrequenceInSingleFileOfProject.get(token) + 1);
										}
									}
								}
							}
						}
					}
					/* caculate the tfidf = tf * idf = tf * log2(N/df). */
					if(!wordsTfInSingleProject.isEmpty()) {
						for(String token : wordsTfInSingleProject.keySet()) {
							if(wordsDocFreq.containsKey(token)) {
								double idf = (double)numProjects / (double)(wordsDocFreq.get(token)) + 1.0;
								idf = Math.log(idf) / Math.log(2.0);
								double tfidf = wordsTfInSingleProject.get(token) * idf;
								wordsTfidfInSingleProject.put(token, tfidf);
							} else {
								System.out.println("---" + token + "- is not included..");
							}
						}
						/* rank the words based on their value of tfidf. */
						Map<String, Double> sortedWordsTfidfInSingleProject = sortByComparatorDouble(wordsTfidfInSingleProject);
						/* output the topNum tokens for WMD */
						int i = 0;
						sbWmd.setLength(0); sbLda.setLength(0);
						PrintWriter rankedWordsInProject = new PrintWriter(new File(f, "rankedWordsInProject.txt"), "UTF-8");
						for(String token : sortedWordsTfidfInSingleProject.keySet()) {
							if(i < topNum) { sbWmd.append(token); sbWmd.append(" "); }
							if(i < topNum - 10) { sbLda.append(token); sbLda.append(" "); }
							i++; rankedWordsInProject.println(token + "," + String.valueOf(sortedWordsTfidfInSingleProject.get(token)));
						}
						rankedWordsInProject.close();
						writer.println(f.getName() + "\t" + sbWmd.toString());
						writerLda.println(f.getName() + "\t" + sbLda.toString());
					} else {
						System.out.println("No words for WMD: " + f.getName());
					}
				}
			}
			writer.close();
			writerLda.close();
			System.out.println("Finish generate Ranked Words Based On TF-IDF For Wmd !! output-> docForWmd.txt docForLda.txt ");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
		}
	}
	
	public void generateRankedWordsBasedOnTfidfForLdaWmdByGroup(String folder, int topNum) {
		try {
			System.out.println("start generateRankedWordsBasedOnTfidfForLdaWmdByGroup..");
			/* read wordsDocFreq HashMap from file By Group*/
			System.out.println("- read wordsDocFreq HashMap from file By Group.. ");
			wordsDocFreqByGroup.clear(); 
			FileInputStream fis = new FileInputStream("wordsDocFreqInProjectName.HashMap"); ObjectInputStream ois = new ObjectInputStream(fis);
			HashMap<String, Integer> newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap); 
			System.out.println("size of wordsDocFreqInProjectName is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInDescription.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInDescription is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInReadme.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInReadme is " + String.valueOf(newMap.size()));
			fis = new FileInputStream("wordsDocFreqInCode.HashMap"); ois = new ObjectInputStream(fis);
			newMap = (HashMap)ois.readObject(); ois.close(); fis.close();
			wordsDocFreqByGroup.add(newMap);
			System.out.println("size of wordsDocFreqInCode is " + String.valueOf(newMap.size()));

			File dir = new File(folder);
			String[] preproFiles = {"projectName.prepro_stem_rs", "description.prepro_stem_rs", "re.prepro_stem_rs", "code.prepro_stem_rs"};
			PrintWriter writer = new PrintWriter("docForWmd.txt", "UTF-8");
			PrintWriter writerLda = new PrintWriter("docForLda.txt", "UTF-8");
			PrintWriter writerProjectDetails = new PrintWriter("projectDetails.txt", "UTF-8");
			PrintWriter writerAllDetails = new PrintWriter("allProjectDetails.csv", "UTF-8");
			StringBuilder sbWmd = new StringBuilder();
			StringBuilder sbLda = new StringBuilder();
			HashMap<String, Double> wordsTfInSingleProject = new HashMap<String, Double>();
			HashMap<String, Double> wordsTfidfInSingleProject = new HashMap<String, Double>();
			HashMap<String, Integer> wordsFrequenceInSingleFileOfProject = new HashMap<String, Integer>();
			
			PrintWriter writer_10 = new PrintWriter("docForWmd_10", "UTF-8");
			PrintWriter writer_15 = new PrintWriter("docForWmd_15", "UTF-8");
			PrintWriter writer_20 = new PrintWriter("docForWmd_20", "UTF-8");
			PrintWriter writer_25 = new PrintWriter("docForWmd_25", "UTF-8");
			PrintWriter writer_30 = new PrintWriter("docForWmd_30", "UTF-8");
			PrintWriter writer_35 = new PrintWriter("docForWmd_35", "UTF-8");
			PrintWriter writer_40 = new PrintWriter("docForWmd_40", "UTF-8");

			for(File f : dir.listFiles()){
				if(f.isDirectory()) {
					wordsTfInSingleProject.clear();
					wordsTfidfInSingleProject.clear();
					writerAllDetails.print(f.getName());
					HashMap<String, Double> wordsInDescription = new HashMap<String, Double>();
					for(int i = 0; i < preproFiles.length; i++) {
						String preproFile = preproFiles[i];
						// do not consider readme file words
						if(new File(f, preproFile).exists() && i != 2) { 
							wordsFrequenceInSingleFileOfProject.clear();
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							writerAllDetails.println("\t \t" + line);
							String[] tokens = line.split(" ", -1);
							for(String token : tokens) {
								if(!token.isEmpty() && token.length() < 11) {
									/* caculate the tfidf = tf * idf = tf * log2(N/df). */
									double idf = (double)numProjects / (double)(wordsDocFreqByGroup.get(i).get(token) + 1.0) + 1.0;
									idf = Math.log(idf) / Math.log(2.0);
									if(!wordsTfidfInSingleProject.containsKey(token)) {
										wordsTfidfInSingleProject.put(token, termsFrequencyWeight[i] * idf);
										wordsFrequenceInSingleFileOfProject.put(token, 1);
									} else {
										if(!wordsFrequenceInSingleFileOfProject.containsKey(token)) {
											wordsTfidfInSingleProject.put(token, wordsTfidfInSingleProject.get(token) + termsFrequencyWeight[i] * idf);
											wordsFrequenceInSingleFileOfProject.put(token, 1);
										} else if (wordsFrequenceInSingleFileOfProject.get(token) < upperLimitNumOfSingleWord) {
											wordsTfidfInSingleProject.put(token, wordsTfidfInSingleProject.get(token) + termsFrequencyWeight[i] * idf);
											wordsFrequenceInSingleFileOfProject.put(token, wordsFrequenceInSingleFileOfProject.get(token) + 1);
										}
									}
								}
							}
							if(i == 1) wordsInDescription = (HashMap)wordsFrequenceInSingleFileOfProject.clone();
						} else {
							writerAllDetails.println("\t \t --");
						}
					}
					//writerAllDetails.print("\n");
					/* rank the words based on their value of tfidf. */
					HashMap<String, Double> sortedWordsTfidfInSingleProject = (HashMap)sortByComparatorDouble(wordsTfidfInSingleProject);
					ArrayList<String> wordsList = new ArrayList<String>();
					ArrayList<String> wordsList_rest = new ArrayList<String>();
					/* give priority rank to the words in description */
					for(String token : sortedWordsTfidfInSingleProject.keySet()) {
						if(wordsInDescription.containsKey(token)) wordsList.add(token);
						else wordsList_rest.add(token);
					}
					wordsList.addAll(wordsList_rest);
					
					/*for(String token : sortedWordsTfidfInSingleProject.keySet()) {
						wordsList.add(token);
					}*/
					
					System.out.println(f.getName() + " feature words size = " + String.valueOf(wordsList.size()));
					/* output the topNum tokens for WMD */
					int i = 0;
					sbWmd.setLength(0); 
					PrintWriter rankedWordsInProject = new PrintWriter(new File(f, "rankedWordsInProject.txt"), "UTF-8");
					for(String token : wordsList) {
						sbWmd.append(token); sbWmd.append(" "); 
						rankedWordsInProject.println(token + "," + String.valueOf(sortedWordsTfidfInSingleProject.get(token)));
						i++; 
						if(i == 10) writer_10.println(f.getName() + "\t" + sbWmd.toString());
						if(i == 15) writer_15.println(f.getName() + "\t" + sbWmd.toString());
						if(i == 20) writer_20.println(f.getName() + "\t" + sbWmd.toString());
						if(i == 25) writer_25.println(f.getName() + "\t" + sbWmd.toString());
						if(i == 30) writer_30.println(f.getName() + "\t" + sbWmd.toString());
						if(i == 35) writer_35.println(f.getName() + "\t" + sbWmd.toString());
						if(i == 40) writer_40.println(f.getName() + "\t" + sbWmd.toString());
					}
					if(i < 10 && i >= 8) writer_10.println(f.getName() + "\t" + sbWmd.toString());
					if(i < 15 && i >= 13) writer_15.println(f.getName() + "\t" + sbWmd.toString());
					if(i < 20 && i >= 18) writer_20.println(f.getName() + "\t" + sbWmd.toString());
					if(i < 25 && i >= 23) writer_25.println(f.getName() + "\t" + sbWmd.toString());
					if(i < 30 && i >= 28) writer_30.println(f.getName() + "\t" + sbWmd.toString());
					if(i < 35 && i >= 33) writer_35.println(f.getName() + "\t" + sbWmd.toString());
					if(i < 40 && i >= 38) writer_40.println(f.getName() + "\t" + sbWmd.toString());

					if(sbWmd.length() > 0) writerAllDetails.println("\t \t" + sbWmd.toString());
					else writerAllDetails.println("\t \t --");
					if(new File(f, "description.prepro").exists()){
						String line_description = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), "description.prepro"))).replaceAll(",", " ");
						writerAllDetails.println("\t \t" + line_description + "\n");
						writerProjectDetails.println(f.getName() + "\t" + line_description);
					}
					else {
						writerAllDetails.println("\t \t --");
						writerProjectDetails.println(f.getName() + "\t" + " ");
					}
					rankedWordsInProject.close();
				}
			}
			writer.close();
			writerLda.close();
			writerProjectDetails.close();
			writerAllDetails.close();
			writer_10.close();
			writer_15.close();
			writer_20.close();
			writer_25.close();
			writer_30.close();
			writer_35.close();
			writer_40.close();

			System.out.println("-- Finish generate Ranked Words Based On TF-IDF For Wmd By Group!! output-> docForWmd.txt docForLda.txt ");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
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
				return -1 * (o1.getValue()).compareTo(o2.getValue());
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
			public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
				return -1 * (o1.getValue()).compareTo(o2.getValue());
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
	
	public void copyProjectsPreprosToNewFolder(String folder) {
		try {
			File newDir = new File("javaProjectsPrepros");
			//if(newDir.exists()) deleteDirectory(newDir);
			if(!newDir.exists()) newDir.mkdir();
			File dir = new File(folder);
			String[] preproFiles = {"projectName", "description", "re", "code"};
			String[] preproFileTypes = {".prepro", ".prepro_stem", ".prepro_stem_rs"};
			for(File f : dir.listFiles()) {
				if(f.isDirectory()) {
					System.out.println("copy project: " + f.getName());
					File projectDir = new File(newDir, f.getName());
					if(!projectDir.exists()) projectDir.mkdir();
					else System.out.println("-- this project has exists: " + f.getName());
					for(String preproFile : preproFiles) {
						for(String preproFileType : preproFileTypes) {
							File originalFile = new File(f, preproFile + preproFileType);
							if(originalFile.exists()) {
								File targetFile = new File(projectDir, preproFile + preproFileType);
								Files.copy(originalFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
							}
						}	
					}
				}
			}
			System.out.println("Finished copy Projects Prepro Files To NewFolder: javaProjectsPrepros");
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void saveHashMapToFile_StringInteger(String fileName, HashMap<String, Integer> hmap) {
		try {
			PrintWriter writer = new PrintWriter(fileName, "UTF-8");
			for(Map.Entry<String,Integer> entry : hmap.entrySet()) {
				writer.println(entry.getKey() + ", " + String.valueOf(entry.getValue()));
			}
			writer.close();
			System.out.println("- save data of hashmap into file: " + fileName);
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException{
		if(args.length != 1) {
			System.out.println("input command : java PreprocessForWord2Vec folder");
			System.exit(0);
		}
		PreprocessForWord2Vec preprocess = new PreprocessForWord2Vec();
		preprocess.numOfProjects(args[0]);
		//preprocess.removeProjectsWithNoReadme(args[0]);
		//preprocess.removeBigProjects(300.0, args[0]);
		//preprocess.downloadDescriptions(args[0]);
		//preprocess.dumpGitLog(args[0]);
		//preprocess.removeProjectsWithNoDescription(args[0]);
		//preprocess.removeProjectsWithNoDescriptionWithNoCheck(args[0]);
		//preprocess.removeProjectsWithNoCodeWithNoCheck(args[0]);
		//preprocess.filterOmittedProjects(args[0], 300.0);
		//preprocess.parseAndStemProjects(args[0]);
		//preprocess.stemProjects(args[0]);
		//preprocess.removeProjectsWithNoDescriptionWithNoCheck(args[0]);
		//preprocess.removeProjectsWithNoCodeWithNoCheck(args[0]);
		//preprocess.parseAndStemProjectsName(args[0]);
		//preprocess.removeTabSpace(args[0]);
		//preprocess.copyProjectsPreprosToNewFolder(args[0]);
		
		//preprocess.wordsStatics(args[0]);
		// preprocess.generateStopWords("english.stop", 0.14);
		//preprocess.removeStopWords(args[0]);

		//preprocess.wordsStaticsByGroup(args[0]);
		//preprocess.wordsStaticsByGroupMore(args[0]);
		//preprocess.generateStopWordsByGroup("english.stop", 0.14);
		preprocess.generateStopWordsByGroupMore("english.stop", 0.35);
		preprocess.removeStopWordsByGroup(args[0]);

		//preprocess.generateDocForWord2Vec(args[0], "forWord2Vec");
		//preprocess.generateDocForWord2VecEvaluation();
		//preprocess.generateDocsForLda(args[0], "forLda");

		//preprocess.generateRankedWordsBasedOnTfidfForLdaWmd(args[0], 25);
		preprocess.generateRankedWordsBasedOnTfidfForLdaWmdByGroup(args[0], 25);
		System.out.println("Done ");
	}
}


