# SeedExtractor

1. There are 5 parameters for the main function:
[file path], [php script path], [result folder path], [MaxPages#], [DEBUG]
For more details, please see code.

2. Before running code, you have to give at least 3 parameters:
[file path], [php script path], [result folder path]

3. Be careful, because Google can monitor your IP, if you use PHP script to send request frequently,
your IP might be blocked, which already happend on me. That's why I let process sleep for a while 
after each call (the sleeping time might not be long enough).