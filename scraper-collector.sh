
echo "<deprecated>"
for f in `find $ANDROID_SDK_PATH/docs/reference -name *.html` 
do
	echo "<file name='$f'>"
	echo "<fields>"
	xmllint --html --xmlout $f 2>/dev/null | xmlstarlet sel -t -c "//p[@class='caution']/../../h4[@class='jd-details-title']/text()"
	echo "</fields>"
	echo "<methods>"
	xmllint --html --xmlout $f 2>/dev/null | xmlstarlet sel -t -c "translate (string(//p[@class='caution']/ancestor::div[h4/@class='jd-details-title']/h4), '&#xA;', ' ')"
	echo "</methods>"
	echo "</file>"
done

echo "</deprecated>"
