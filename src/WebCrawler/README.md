#WebCrawler

/**
 * USAGE: java Crawler [-path savePath] [-time duration] [-id jobID]
 * Please pay attention:
 * This program assumes that under the directory variable 'savePath' the user provides,
 * the following two sub-directories have been created: (please use the same capitalization)
 * a directory called 'hashSets', containing the external hashSets from last round, or empty if it's the first round
 * a directory called 'roots', containing url root files named as 'root_1', 'root_2'... the number of such files
 * should be the same with the number of rounds the program to be run, so if we plan to run the program
 * 200 times, then the files 'root_1' - 'root_200' should all exist in this directory
 * The unit of duration is second
 * For example, to compile and run: (please cd to src)
 * javac WebCrawler/Crawler.java
 * java WebCrawler/Crawler -path ../results/ -time 180 -id 1
 */
 
