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
import java.lang.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.*;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

public class PreprocessForWord2Vec {
	int numLoadedFiles = 0;
	int maxNumLoadedFiles = 2000;
	int numProjects = 0;
	StringBuilder descriptionSb, codeSb, readMeSb, commitSb;
	String projectsDirectory;
	SnowballStemmer stemmer;
	HashMap<String, Integer> wordsMap;
	HashMap<String, Integer> wordsDocFreq;
	HashSet<String> stopWords;
	HashSet<String> initialStopWords;
 
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
		stopWords = new HashSet<String>();
		initialStopWords = new HashSet<String>();
		 
	}
	public void numOfProjects(String folder) {
		projectsDirectory = folder;
		File dir = new File(projectsDirectory);
		numProjects = dir.listFiles().length;
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
	
	public double getFileFolderSize(File dir) {
		double size = 0.0;
		if (dir.isDirectory() && !dir.isHidden() && dir.list() != null && dir.listFiles().length != 0) {
			for (File file : dir.listFiles()) {
				if (file.isFile()) {
					size += (double)file.length() / 1024 / 1024;
				} else
					size += getFileFolderSize(file);
			}
		} else if (dir.isFile()) {
			size += (double)dir.length() / 1024 / 1024;
		} else ;
		return size;
	}
	
	public void removeBigProject(double upLimitProjectSize, String folder) {
	  File dir = new File(folder);
	  for(File f : dir.listFiles()){
	    double size = getFileFolderSize(f);
	    if(size > upLimitProjectSize) {
	      System.out.printf("Big file size = %f. Remove this project \n", size);
	      System.out.println("   .." + f.getName());
	      deleteDirectory(f);
	    } else {
	      System.out.print("Yes Moderate file.  ");
	      System.out.println("   .." + f.getName());
	    }
	  }
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
  	    //System.out.println(line);
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
	
	public String loadDescription(String projectDirectory) {
	  if(descriptionExist(projectDirectory)) return "";
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
			String repositoryUrl = reader.readLine(); // for example: git@github.com:scy/dotscy.git
			if(repositoryUrl != null && repositoryUrl.length() > 14 && repositoryUrl.indexOf(":") >= 0 && repositoryUrl.indexOf(".git") >= 0)
  			return retrieveDescriptionFromUrl(repositoryUrl.substring(repositoryUrl.indexOf(":") + 1, repositoryUrl.indexOf(".git")));
		  return "";
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		} catch (InterruptedException e) {
			e.printStackTrace();
			return "";
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
		//System.out.println(text + " PCW " + sb.toString());
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
      StringBuilder sb = new StringBuilder(); String line;  String author; String date; String emailAddress; String message;
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
			message = TokenizerNormalizationStemming(sb.toString());
			return message;
    }catch(IOException e){
      System.err.format("[Error]Failed to open file %s ", fileName);
      e.printStackTrace();
			return null;
    }
  }
	
	// recursively load files in a directory
	public void LoadFilesFromFolder(String folder, String prefix, String suffix) {
		File dir = new File(folder);
		HashSet<File> set = new HashSet<File>();
		Queue<File> queue = new LinkedList<File>();
		queue.add(dir); set.add(dir); int i = 0;
		double folderSize = 0.0; double folderSizeThreshold = 20.0;
		while(!queue.isEmpty() && folderSize < folderSizeThreshold) {
			dir = queue.poll(); i++;
			//System.out.print(String.valueOf(i) + " ");
			if(dir.list() == null || dir.listFiles().length == 0) continue;
			for(File f : dir.listFiles()){
				if(f != null && f.isFile() && !f.isHidden()) {
					if(i == 1 && f.getName().toLowerCase().startsWith(prefix)) {
						//System.out.println(numLoadedFiles + " load README file"+" : " + f.getName());
						readMeSb.append(LoadReadMeFile(f.getAbsolutePath()));
						numLoadedFiles ++;
						folderSize += f.length() / 1024.0 / 1024.0;
					} else if(f.getName().endsWith(suffix)) {
						//System.out.println(numLoadedFiles + " load code file"+" : "+f.getName());
						codeSb.append(loadCodeFile(f.getAbsolutePath()));
						numLoadedFiles ++;
						folderSize += f.length() / 1024.0 / 1024.0;
					} /*else if(f.getName().toLowerCase().startsWith("change_log")) {
						commitSb.append(loadCommitFile(f.getAbsolutePath()));
						numLoadedFiles ++;
						folderSize += f.length() / 1024.0 / 1024.0;
					} */
				}
				else if(f != null && f.isDirectory() && !f.isHidden()) {
					if(!set.contains(f)) {
						queue.add(f);
						set.add(f);
					}
				}
				if(folderSize > folderSizeThreshold) break;
				//LoadFilesFromFolder(f.getAbsolutePath(), prefix, suffix);
			}
		}
		/* parse the description of this project and append to StringBuilder */
		descriptionSb.append(loadDescription(folder));
		System.out.println(" size = " + String.valueOf(folderSize));
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
	
	// parse each project folder and save the preprocessed data in their own folder.
	public void parseProjects(String folder) {
	  try{
	    File dir = new File(folder);
	    long limitedRate = 1400;
  		int i = 1;
  		for(File f : dir.listFiles()){
  			/* detect project folder */
  			//if(f.isDirectory() && !hasBeenParsed(f)) {
  			if(f.isDirectory()) {
  			  long startTime = System.nanoTime();
  				System.out.println("project #"+ String.valueOf(i) + f.getAbsolutePath());
  				descriptionSb.setLength(0);
  				readMeSb.setLength(0);
  				codeSb.setLength(0);
  				//commitSb.setLength(0);
  				LoadFilesFromFolder(f.getAbsolutePath(), "readme", "java");
  				if(!descriptionExist(f.getAbsolutePath()) && descriptionSb.length() > 0)
  				  saveStringIntoFileUnderFolder(f.getAbsolutePath(), "description.prepro", descriptionSb.toString());
  				saveStringIntoFileUnderFolder(f.getAbsolutePath(), "re.prepro", readMeSb.toString());
  				saveStringIntoFileUnderFolder(f.getAbsolutePath(), "code.prepro", codeSb.toString());
  				//saveStringIntoFileUnderFolder(f.getAbsolutePath(), "commit.prepro", commitSb.toString());
  				
  				long endTime = System.nanoTime();
  				if(endTime - startTime < limitedRate) Thread.sleep(limitedRate - (endTime - startTime));
  				
  			} else System.out.println("project #"+ String.valueOf(i));
  			i ++;
  			//if(i >= 3000) break;
  		}
	  } catch(InterruptedException e) {
	    e.printStackTrace();
	  }
		
	}
	
	public void removeProjectsWithNoDescription(String folder) {
	    File dir = new File(folder);
	    int n = 0;
  		for(File f : dir.listFiles()){
  			if(f.isDirectory()) {
  			  File file = new File(f, "description.prepro");
			    if(!file.exists()) {
			      String line = loadDescription(f.getAbsolutePath());
			      if(line.trim().length() > 0) {
			        saveStringIntoFileUnderFolder(f.getAbsolutePath(), "description.prepro", line);
			        System.out.println(f.getName() + "  description: " + line);
			      } else {
			        n ++;
			        System.out.println("No description. Delete project: " + f.getName() + "  " + String.valueOf(n));
			        deleteDirectory(f);
			      }
			    }
  			}
  		}
	}
	
	public String TokenizerNormalizationStemming(String text){
		if(text == null || text.length() == 0)return "";
		StringBuilder sb = new StringBuilder();
		text = text.replaceAll("[^a-zA-Z\\s]", " ");
		String[] tokens = text.split("   ");
		//String[] tokens = text.split("[^a-zA-Z']+");
		
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
	
	public void saveObjectToFile(String fileName, Object obj){
		try{
			System.out.println("Save Object " + fileName);
			FileOutputStream fos =new FileOutputStream(fileName);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
            oos.close();
			fos.close();
		}catch(IOException e){
            System.err.format("[Error]Failed to open or create file ");
            e.printStackTrace();
        }
	}

	public void wordsStatics(String folder) {
		try {
			//String[] preproFiles = {"re.prepro", "code.prepro", "commit.prepro"};
			String[] preproFiles = {"description.prepro", "re.prepro", "code.prepro"};
			System.out.println("start words statics...");
			File dir = new File(folder);
			int projectsCount = 0;
			HashSet<String> singleProjectWords = new HashSet<String>();
			PrintWriter writer = new PrintWriter("projectWords_readMe.csv", "UTF-8");
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					projectsCount ++;
					singleProjectWords.clear();
					//HashMap<String, Integer> projectWords = new HashMap<String, Integer>();
					for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							if(preproFile.equals("description.prepro"))
							  line = TokenizerNormalizationStemming(line);
							if(preproFile.equals("re.prepro"))
								writer.println(line);
							String[] tokens = line.split(" ");
							for(String token : tokens) {
								if(token != null && token.length() > 0 && token.length() < 10) {
									if(!singleProjectWords.contains(token)) {
										singleProjectWords.add(token);
										if(!wordsDocFreq.containsKey(token)) wordsDocFreq.put(token, 1);
										else wordsDocFreq.put(token, wordsDocFreq.get(token) + 1);
									}
								}
							}
						} else System.out.println(f.getAbsolutePath() + "  " + preproFile);
					}
				}
			}
			numProjects = projectsCount;
			saveObjectToFile("wordsDocFreq.HashMap", wordsDocFreq);
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
			Map<String,Integer> sortedWordsMap = sortByComparator(wordsDocFreq); // sort the wordsMap based on value
			/* write wordsStatics into file */
			int offNum = (int) (offset * numProjects);
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
			saveObjectToFile("initialStopWords.HashSet", initialStopWords);
			/* save new stopWords file */
			writer = new PrintWriter("updatedEnglish.stop", "UTF-8");
			for(String keyToken : stopWords) {
				writer.println(keyToken);
			}
			writer.close();
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
			String[] preproFiles = {"description.prepro", "re.prepro"};
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
			int upperLimitNumOfSingleWord = 3;
			for(File f : dir.listFiles()){
				/* detect project folder */
				if(f.isDirectory()) {
					for(String preproFile : preproFiles) {
						if(new File(f, preproFile).exists()) {
							singleProjectWords.clear();
							sb.setLength(0);
							String line = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath(), preproFile)));
							if(preproFile.equals("description.prepro")) {
							  /* for description.prepro */
							  line = TokenizerNormalizationStemming(line);
							  String[] tokens = line.split(" ");
  							for(String token : tokens) {
  								if(!token.isEmpty() && token.length() < 10) {
  									if(!initialStopWords.contains(token)) {
  										sb.append(token + " ");
  									}
  								}
  							}
							} else {
							  /* for re.prepro */
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
	
	public void generateDocsForLda(String folder, String destFolder) {
	  try {
	    String[] preproFiles = {"description.prepro_rs", "re.prepro_rs"};
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
	  } catch (FileNotFoundException ex) {
      ex.printStackTrace();
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
	
	public static void main(String[] args) throws IOException{
		if(args.length != 1) {
			System.out.println("input command : java PreprocessForWord2Vec folder");
			System.exit(0);
		}
		PreprocessForWord2Vec preprocess = new PreprocessForWord2Vec();
		//preprocess.removeProjectsWithNoReadme(args[0]);
		//preprocess.removeBigProject(200.0, args[0]);
		//preprocess.dumpGitLog(args[0]);
		preprocess.parseProjects(args[0]);
		//preprocess.removeProjectsWithNoDescription(args[0]);
		preprocess.numOfProjects(args[0]);
		preprocess.wordsStatics(args[0]);
		preprocess.generateStopWords("english.stop", 0.06);
		preprocess.removeStopWords(args[0]);
		preprocess.generateDocsForLda(args[0], "forLda");
		System.out.println("Done ");
	}
}


