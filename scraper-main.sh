#!/bin/bash
echo "<deprecated>"
bash scraper-collector.sh "$1" | xmlstarlet sel -t -c  "//file" 
echo "</deprecated>"
