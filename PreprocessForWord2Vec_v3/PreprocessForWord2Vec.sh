javac -cp '.:libstemmer.jar' PreprocessForWord2Vec.java
nohup java -cp '.:libstemmer.jar' PreprocessForWord2Vec ../java_projects/ >out&
