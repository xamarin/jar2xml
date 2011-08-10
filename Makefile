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

8.xml:
	wget --output-document=8.xml "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/8.xml;hb=HEAD" || exit 1
	mono-xmltool --prettyprint 8.xml > 8_.xml || exit 1
	xmlstarlet c14n 8_.xml > 8.xml || exit 1

