# SeedExtractor

1. There are 5 parameters for the main function:
[file path], [php script path], [result folder path], [MaxPages#], [NIGHTMODEL], [DEBUG]
For more details, please see code.

2. Before running code, you have to give at least 3 parameters:
[file path], [php script path], [result folder path]

3. Be careful, because Google can monitor your IP, if you use PHP script to send request frequently,
your IP might be blocked, which already happend on me. That's why I let process sleep for a while 
after each call (the sleeping time might not be long enough).

4. I add night model, so you can run the program when you sleep in the night and it can figure out
when google block you IP and modify the thread's sleep time automatically.
This model will be open defautly. If you want to close it, for the fiveth parameter,
it should be "-off".