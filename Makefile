TARGET=jar2xml.jar 

all: $(TARGET) 

clean:
	-rm -rf obj
	-rm $(TARGET)

sources = \
	AndroidDocScraper.java \
	IDocScraper.java \
	JavaArchive.java \
	JavaClass.java \
	JavaPackage.java \
	Start.java

$(TARGET): $(sources)
	-rm -rf obj
	mkdir -p obj
	javac -g -d obj $(sources)
	jar cfe "$@" jar2xml.Start -C obj/ .

test-8: 8.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-8/android.jar --out=_8.xml --docpath=$(ANDROID_SDK_PATH)/docs/reference || exit 1
	mono-xmltool --prettyprint _8.xml > _8_.xml || exit 1
	xmlstarlet c14n _8_.xml > _8.xml || exit 1
	rm _8_.xml

8.xml:
	wget --output-document=8.xml "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/8.xml;hb=HEAD" || exit 1
	mono-xmltool --prettyprint 8.xml > 8_.xml || exit 1
	xmlstarlet c14n 8_.xml > 8.xml || exit 1
	rm 8_.xml

# These rules are to get diff between 10 and 11.
test-11: 10.xml 
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-11/android.jar --out=_11.xml --docpath=$(ANDROID_SDK_PATH)/docs/reference || exit 1
	mono-xmltool --prettyprint _11.xml > _11_.xml || exit 1
	xmlstarlet c14n _11_.xml > _11.xml || exit 1
	rm _11_.xml

10.xml:
	wget --output-document=10.xml "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/10.xml;hb=HEAD" || exit 1
	mono-xmltool --prettyprint 10.xml > 10_.xml || exit 1
	xmlstarlet c14n 10_.xml > 10.xml || exit 1
	rm 10_.xml

