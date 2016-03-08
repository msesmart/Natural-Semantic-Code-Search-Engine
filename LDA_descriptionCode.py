
from nltk.tokenize import RegexpTokenizer
from stop_words import get_stop_words
from nltk.stem.porter import PorterStemmer
import gensim
from gensim import corpora, models

import sys
import os
import numpy as np

'''
  follow the instruction of
    https://rstudio-pubs-static.s3.amazonaws.com/79360_850b2a69980c4488b1db95987a24867a.html
  LDA for multiple text files.
  
  Feb.28 2016
'''
__author__ = 'Chengjun Yuan @UVa'

def loadData(folder):
  doc_set = []
  if(not os.path.exists(folder)):
    print folder, " does not exist"
    sys.exit()
  allFiles = os.listdir(folder)
  for file in allFiles:
    print file, " "
    fo = open(os.path.join(folder, file), "r+")
    line= fo.read()
    doc_set.append(line)
    fo.close()
  return doc_set

def preprocessData(doc_set):
  tokenizer = RegexpTokenizer(r'\w+')
  # create English stop words list
  #en_stop = get_stop_words('en')
  # Create p_stemmer of class PorterStemmer
  #p_stemmer = PorterStemmer()
  # list for tokenized documents in loop
  texts = []
  # loop through document list
  for i in doc_set:
    # clean and tokenize document string
    raw = i.lower()
    tokens = tokenizer.tokenize(raw)

    # remove stop words from tokens
    #stopped_tokens = [i for i in tokens if not i in en_stop]
    # stem tokens
    #stemmed_tokens = [p_stemmer.stem(i) for i in stopped_tokens]
    
    # add tokens to list
    texts.append(tokens)
  return texts
  

'''
# create sample documents
doc_a = "Brocolli is good to eat. My brother likes to eat good brocolli, but not my mother."
doc_b = "My mother spends a lot of time driving my brother around to baseball practice."
doc_c = "Some health experts suggest that driving may cause increased tension and blood pressure."
doc_d = "I often feel pressure to perform well at school, but my mother never seems to drive my brother to do better."
doc_e = "Health professionals say that brocolli is good for your health."

# compile sample documents into a list
#doc_set = [doc_a, doc_b, doc_c, doc_d, doc_e]
'''

if __name__ == "__main__":
  if len(sys.argv) != 2:
    print "Usage: python LDA_descriptionCode.py projectDescriptionPath"
    sys.exit()
  
  numTopics = 100
  
  print "load data from directory - ", sys.argv[1]
  doc_set = loadData(sys.argv[1])
  
  print "preprocess data: tokenize "
  texts = preprocessData(doc_set)
  
  # turn our tokenized documents into a id <-> term dictionary
  dictionary = corpora.Dictionary(texts)
    
  # convert tokenized documents into a document-term matrix
  corpus = [dictionary.doc2bow(text) for text in texts]

  # generate LDA model
  #ldamodel = gensim.models.ldamodel.LdaModel(corpus, num_topics = 200, id2word = dictionary, passes = 30)
  ldamodel = gensim.models.LdaMulticore(corpus, num_topics = numTopics, id2word = dictionary, passes = 20, workers = 7)
  
  print(ldamodel.print_topics(num_topics = numTopics, num_words = 20))
  f = open("ldamodel.txt", "w")
  for line in ldamodel.print_topics(num_topics = numTopics, num_words = 20):
    f.write(repr(line))
    f.write("\n")
  f.close()
  
  topic_docs = [[] for x in range(numTopics)]
  i = 0
  doc_topic_probability = 0.0
  topicId = 0
  for doc_bow in corpus:
    #doc_topics = ldamodel[doc_bow]
    doc_topics = ldamodel.get_document_topics(doc_bow)
    #print i, type(doc_topics)
    if len(doc_topics) == 0: continue
    print doc_topics[0]
    doc_topic_probability = 0.0
    for topicId_probability in doc_topics:
      if topicId_probability[1] > doc_topic_probability:
        doc_topic_probability = topicId_probability[1]
        topicId = topicId_probability[0]
    #topic_docs[topicId].append(doc_bow)
    topic_docs[topicId].append(doc_set[i])
    topic_docs[topicId].append(doc_topic_probability)
    i = i + 1
	
  f = open("lda_topics_projects.csv", "w")
  i = 0
  for line in ldamodel.print_topics(num_topics = numTopics, num_words = 20):
    f.write(repr(line))
    if len(topic_docs[i]) > 0:
      j = 0
      while j < len(topic_docs[i]):
        f.write(",")
        f.write(topic_docs[i][j])
        j = j + 1
        f.write(",")
        f.write(str(topic_docs[i][j]))
        j = j + 1
        f.write("\n")
    else:		
      f.write("\n")
    i = i + 1
  f.close()