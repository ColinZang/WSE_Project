# Indexter

1. There are 7 parameters for the main function:
-d [data folder path] -r [result folder path] -ct [crawler thread num] 
-cj [crawler job num] -t [indexter thread num] -nm [no number model] 
-s [path for stoplist file]

2. -d [data folder path]: is the main folde for data, includes folder for each job 
and each job also includes folders for each creawler threads

3. -ct [crawler thread num]: how many threads used in crawler, because indexter thread will 
find their own working folders based on it.

4. -cj [crawler job num]: how many jobs created by crawler

5. -t [indexter thread num]: how many threads you want to create for indexter, 
default value is 100

6. -nm [no number model]: whether save number into index file, defaultly open this model and 
will not save number, "off" means close the model, otherwise it will open

7. -s [path for stoplist file]: if give this parameter, program will open using stoplist
model automatically. There are 3 stopword lists in source folder. Suggest to use use 
"MediumStopList"