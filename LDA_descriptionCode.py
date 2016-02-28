
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
  ldamodel = gensim.models.LdaMulticore(corpus, num_topics = 200, id2word = dictionary, passes = 30, workers = 7)
  
  print(ldamodel.print_topics(num_topics = 200, num_words = 30))
  f = open("ldamodel.txt", "w")
  f.write(ldamodel.print_topics(num_topics = 200, num_words = 30))
  f.close()