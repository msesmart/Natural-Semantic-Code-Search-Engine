import subprocess
import shlex
import csv


# read URLs from javaProjectsURLs.csv 
def readUrls(fileName):
	csvFile = open(fileName)
	csvRows = csv.reader(csvFile, delimiter=',')
	URLs = []
	i = 0
	for row in csvRows:
		if i == 0:		# The first row is column 
			i += 1
			continue
		index = row[1].find('repos') + 6
		URL = row[1][index:]
		URLs.append(URL)
		if i < 20:
			print URL
		i = i + 1
	
	csvFile.close
	return URLs

URLs = readUrls('javaProjectsURLs.csv')
print 'URL',len(URLs)

i=0
for oneURL in URLs:
	command = 'git clone git@github.com:'+oneURL+'.git'
	print command
	args=shlex.split(command)
	print args
	p=subprocess.Popen(args)
	p.wait()
	i=i+1
	if i>20:  # the number of projects to download
		break

	
