#WebCrawler

 1) USAGE: java Crawler [-path savePath] [-time duration] [-id jobID]
 
 2) Please put 'PageCompress' directory besides the 'WebCrawler' directory under the same parent directory, and download "jsoup-1.8.3.jar". For example, to compile and run, please cd to the parent directory and type:
 
 javac -cp "../lib/jsoup-1.8.3.jar" PageCompress/*.java
 
 javac -cp "../lib/jsoup-1.8.3.jar:." WebCrawler/*.java
 
 java -cp "../lib/jsoup-1.8.3.jar:." WebCrawler/Crawler -path ../results -time 5 -id 1
 
 3) The unit of duration is minute. Search limit is not used because it may never be reached.
 
 4) Under the directory variable 'savePath' the user provides, the following two sub-directories should have been created before running: (please use the same capitalization)
 
 a directory called 'hashSets', containing the external hashSets from last round, or empty if it's the first round
 
 a directory called 'roots', containing url root files named as 'root_1', 'root_2'... the number of such files should be the same with the number of rounds the program to be run, so if we plan to run the program 200 times, then the files 'root_1' - 'root_200' (no extensions) should all exist in this directory
 
 
 
 
