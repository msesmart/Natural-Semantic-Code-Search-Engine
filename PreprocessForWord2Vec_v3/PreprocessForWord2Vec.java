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
	Double[] termsFrequencyWeight = {2.0, 4.0, 2.0, 1.0};
	StringBuilder descriptionSb, codeSb, readMeSb, commitSb;
	String projectsDirectory;
	SnowballStemmer stemmer;
	HashMap<String, Integer> wordsMap;
	HashMap<String, Integer> wordsDocFreq;
	HashMap<String, Double> wordsTermFreq;
	HashSet<String> stopWords;
	HashSet<String> initialStopWords;
	HashSet<String> omittedProjects;
	HashMap<String, Integer> projectsNo; // each project has its unique No.  projectName -> No.
	String[] noProjects; // No. -> projectName
	
 
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
		wordsTermFreq = new HashMap<String, Double>();
		stopWords = new HashSet<String>();
		initialStopWords = new HashSet<String>();
		omittedProjects = new HashSet<String>();
		projectsNo =  new HashMap<String, Integer>();
	}
	
	public void numOfProjects(String folder) {
		try{
			projectsDirectory = folder;
			File dir = new File(projectsDirectory);
			File[] projectDirs = dir.listFiles();
			numProjects = projectDirs.length;
			noProjects = new String[numProjects];
			
			File projectsNoFile = new File(dir, "projectsNo.HashMap");
			if(projectsNoFile.exists()) {
				System.out.println("projectsNoFile exists... read projectsNo.HashMap... ");
				FileInputStream fis = new FileInputStream(projectsNoFile); ObjectInputStream ois = new ObjectInputStream(fis);
				projectsNo = (HashMap)ois.readObject(); ois.close(); fis.close();
				for(String projectName_ : projectsNo.keySet()) {
					int i = projectsNo.get(projectName_) - 1;
					noProjects[i] = projectName_;
				}
			} else {
				System.out.println("projectsNoFile No exists...");
				for(int i = 0; i < numProjects; i ++) {
					noProjects[i] = projectDirs[i].getName();
					projectsNo.put(noProjects[i], i + 1);
				}
				saveObjectToFile(folder, "projectsNo.HashMap", projectsNo);
			}
			
			PrintWriter writer = new PrintWriter("projectsNameList.txt", "UTF-8");
			for(int i = 0; i < numProjects; i++) {
				writer.println(noProjects[i]);
			}
			writer.close();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch(ClassNotFoundException c) {
			System.out.println("Class not found"); c.printStackTrace(); return;
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
				str = TokenizerNormalization(str);
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
				str = TokenizerNormalization(str);
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
				}else if(str.startsWith("public class")) {
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
			message = TokenizerNormalization(sb.toString());
			return message;
		}catch(IOException e){
			System.err.format("[Error]Failed to open file %s ", fileName);
			e.printStackTrace();
			return null;
		}
	}
	
	/* stemmer the words in the string sentence. */
	public String stringStemmer(String text) {
		SnowballStemmer stemmer_ = new englishStemmer();
		String[] tokens = text.split(" ");
		StringBuilder sb = new StringBuilder();
		for(String token : tokens) {
			token = token.toLowerCase();
			//if(token.matches(".*\\d+.*")) token = "";
			if(!token.isEmpty()){
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
		queue.add(projectDir); set.add(projectDir); int i = 0;
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
	//try{
	    File dir = new File(folder);
		int threads = Runtime.getRuntime().availableProcessors();
		System.out.println("number of threads: " + String.valueOf(threads));
		ExecutorService service = Executors.newFixedThreadPool(threads / 2);
		
  		int i = 1;
  		for(final File f : dir.listFiles()){
  			final int index = i; i++;
		    Callable<Integer> callable = new Callable<Integer>() {
		        public Integer call() throws Exception {
		            if(f.isDirectory() && !(new File(f, "code.prepro")).exists()) {
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
	public String TokenizerNormalization(String text){
		if(text == null || text.length() == 0)return "";
		StringBuilder sb = new StringBuilder();
		//text = text.replaceAll("[^a-zA-Z\\s]", " ");
		text = text.replaceAll("[^a-zA-Z]", " ");
		String[] tokens = text.split(" ");
		//String[] tokens = text.split("[^a-zA-Z']+");
		
		for(String token : tokens) {
			//token=token.replaceAll("\\W+", "");
			//token = token.replaceAll("\\p{Punct}+","");
			token = token.toLowerCase();
			//if(token.matches(".*\\d+.*")) token = "";
			if(!token.isEmpty()){
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
			System.out.println("Save Object " + fileName + "in folder: " + folder);
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
	
	public void wordsStatics(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"projectName.prepro_stem", "description.prepro_stem", "re.prepro_stem", "code.prepro_stem"};
			
			System.out.println("start words statics...");
			File dir = new File(folder);
			int projectsCount = 0;
			HashMap<String, Integer> wordsSetInSingleProject = new HashMap<String, Integer>();
			PrintWriter writer = new PrintWriter("projectWords_readMe.csv", "UTF-8");
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
							if(preproFiles[i].startsWith("re.prepro")) writer.println(line);
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
			numProjects = projectsCount;
			saveObjectToFile(null, "wordsDocFreq.HashMap", wordsDocFreq);
			saveObjectToFile(null, "wordsTermFreq.HashMap", wordsTermFreq);
			System.out.println("Finish words statics !! ");
			writer.close();
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
							if(preproFile.startsWith("description")) {
								/* for description.prepro */
								//line = TokenizerNormalizationStemming(line);
								String[] tokens = line.split(" ");
								for(String token : tokens) {
									if(!token.isEmpty() && token.length() < 10) {
										if(!initialStopWords.contains(token)) {
											sb.append(token + " ");
										}
									}
								}
							} else {
								/* for re.prepro and code.prepro*/
								String[] tokens = line.split(" ");
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
	
	public void generateDocForWord2Vec(String folder, String destFolder) {
		try {
			String[] preproFiles = {"description.prepro_stem_rs", "re.prepro_stem_rs", "code.prepro_stem_rs"};
			File dir = new File(folder);
			File destDir = new File(destFolder);
			if(destDir.exists()) deleteDirectory(destDir);
			destDir.mkdir();
			StringBuilder sb = new StringBuilder(); String line;
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
			writer.close();
			System.out.println("generateDocForWord2Vec finished !!");
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
	
	public void generateRankedWordsBasedOnTfidfForWmd(String folder, int topNum) {
		try {
			// load wordsDocFreq.HashMap
			System.out.println("load wordsDocFreq.HashMap ");
			FileInputStream fis = new FileInputStream("wordsDocFreq.HashMap"); ObjectInputStream ois = new ObjectInputStream(fis);
			wordsDocFreq = (HashMap)ois.readObject(); ois.close(); fis.close();
			// load wordsTermFreq.HashMap
			System.out.println("load wordsTermFreq.HashMap ");
			fis = new FileInputStream("wordsTermFreq.HashMap"); ois = new ObjectInputStream(fis);
			wordsTermFreq = (HashMap)ois.readObject(); ois.close(); fis.close();
			
			File dir = new File(folder);
			String[] preproFiles = {"projectName.prepro_stem_rs", "description.prepro_stem_rs", "re.prepro_stem_rs", "code.prepro_stem_rs"};
			PrintWriter writer = new PrintWriter("docForWmd.txt", "UTF-8");
			StringBuilder sb = new StringBuilder();
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
								if(!token.isEmpty()) {
									if(!wordsTfInSingleProject.containsKey(token)) {
										wordsTfInSingleProject.put(token, termsFrequencyWeight[i]);
										wordsFrequenceInSingleFileOfProject.put(token, 1);
									} else {
										if(!wordsFrequenceInSingleFileOfProject.containsKey(token)) {
											wordsTfInSingleProject.put(token, wordsTfInSingleProject.get(token) + termsFrequencyWeight[i]);
											wordsFrequenceInSingleFileOfProject.put(token, 1);
										} else if (wordsFrequenceInSingleFileOfProject.get(token) < upperLimitNumOfSingleWord) {
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
						sb.setLength(0);
						PrintWriter rankedWordsInProject = new PrintWriter(new File(f, "rankedWordsInProject.txt"), "UTF-8");
						for(String token : sortedWordsTfidfInSingleProject.keySet()) {
							if(i < topNum) { sb.append(token); sb.append(" "); }
							i++; rankedWordsInProject.println(token + "," + String.valueOf(sortedWordsTfidfInSingleProject.get(token)));
						}
						rankedWordsInProject.close();
						writer.println("\"" + String.valueOf(projectsNo.get(f.getName())) + "\"" + "\t" + sb.toString());
					} else {
						System.out.println("No words for WMD: " + f.getName());
					}
				}
			}
			writer.close();
			System.out.println("Finish generate Ranked Words Based On TF-IDF For Wmd !! output-> docForWmd.txt ");
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
			if(newDir.exists()) deleteDirectory(newDir);
			newDir.mkdir();
			File dir = new File(folder);
			String[] preproFiles = {"projectName", "description", "re", "code"};
			String[] preproFileTypes = {".prepro", ".prepro_stem", ".prepro_stem_rs"};
			for(File f : dir.listFiles()) {
				if(f.isDirectory()) {
					System.out.println("copy project: " + f.getName());
					File projectDir = new File(newDir, f.getName());
					projectDir.mkdir();
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
		//preprocess.filterOmittedProjects(args[0], 300.0);
		//preprocess.parseAndStemProjects(args[0]);
		//preprocess.parseAndStemProjectsName(args[0]);
		//preprocess.copyProjectsPreprosToNewFolder(args[0]);
		//preprocess.removeTabSpace(args[0]);
		preprocess.wordsStatics(args[0]);
		preprocess.generateStopWords("english.stop", 0.15);
		preprocess.removeStopWords(args[0]);
		preprocess.generateDocForWord2Vec(args[0], "forWord2Vec");
		preprocess.generateDocsForLda(args[0], "forLda");
		preprocess.generateRankedWordsBasedOnTfidfForWmd(args[0], 30);
		System.out.println("Done ");
	}
}


