<?php

// check for all required arguments
// first argument is always name of script!

if ($argc != 3) {
die("Parameter number is wrong");
}

$query = $argv[1];
$start = $argv[2];


$url = "http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=$query&start=$start";

$body = file_get_contents($url);
$json = json_decode($body);

for($x=0;$x<count($json->responseData->results);$x++){

echo "Result ".($start+$x+1)."\n";

echo "URL: ";
echo $json->responseData->results[$x]->url;
echo "\n";
echo "Title: ";
echo $json->responseData->results[$x]->title;
echo "\n";

}

?>