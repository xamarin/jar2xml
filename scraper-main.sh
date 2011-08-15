echo "<deprecated>"
bash scraper-collector.sh | xmlstarlet sel -t -c "//file[normalize-space(fields/text()) or normalize-space(methods/text())]" 
echo "</deprecated>"
