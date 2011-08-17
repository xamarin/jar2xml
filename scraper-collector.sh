#!/bin/bash

if [ "$1" ]; then
	export docpath="$1"
else
	export docpath="$ANDROID_SDK_PATH/docs/reference"
fi

echo "<deprecated basepath='$docpath'>"
for f in `find $docpath -name *.html` 
do
	export len=`expr length "$docpath" + 1`
	echo "<file name='${f:$len}'>"
	echo "<fields>"
	xmllint --html --xmlout $f 2>/dev/null | xmlstarlet sel -t -c "//p[@class='caution']/../../h4[@class='jd-details-title']/text()"
	echo "</fields>"
	echo "<methods>"
	xmllint --html --xmlout $f 2>/dev/null | xmlstarlet sel -t -c "translate (string(//p[@class='caution']/ancestor::div[h4/@class='jd-details-title']/h4), '&#xA;', ' ')"
	echo "</methods>"
	echo "</file>"
done

echo "</deprecated>"
