# WebCrawler

 USAGE: java Crawler [-path savePath] [-max searchLimit] [-id jobID]
 
 Please pay attention:
 
 This program assumes that under the directory variable 'savePath' the user provides, the following things have been created: (please use the same capitalization)
 
 A directory called 'hashSets', containing the external hashSets from last round, or empty if it's the first round
 
 A file containing the root urls, called 'root_1' or 'root_2'... the number should be the same with the variable 'jobID' the user provides. When each round ends, it will generate the root file for next round, so actually only root_1 (no extension) should be created
 
 For example, to compile and run: (please cd to src)
 javac WebCrawler/Crawler.java
 java WebCrawler/Crawler -path ../results/ -max 2500 -id 1
 
