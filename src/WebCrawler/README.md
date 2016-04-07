# WebCrawler

1. To compile and run, cd to src folder: 

   javac WebCrawler/Crawler.java

   java WebCrawler/Crawler -root [seedFile] -path [resultFolder] -max [searchLimit]

   For example:

   java WebCrawler/Crawler -root ../result/SeedExtractor/seedResult.txt 
                           -path ../result/WebCrawler 
                           -max 10000

2. Please remember to clear the result folder before each run, or change to another folder. Otherwise, the external hashset generated from last run still exists and the crawler will stop very early.

3. The crawler might be slow sometimes, it becomes more stable after downloading the first 1000 pages. The search limit is an approximation, you can manually stop the program.
