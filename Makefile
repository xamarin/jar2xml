TARGET=jar2xml.jar 

all: $(TARGET) 

clean:
	-rm -rf obj
	-rm $(TARGET)
	-rm -rf tmpout
	-rm -rf annotations
	-rm -rf scraper.exe scraper.exe.mdb

sources = \
	AndroidDocScraper.java \
	IDocScraper.java \
	JavaArchive.java \
	JavaClass.java \
	JavaPackage.java \
	Start.java

$(TARGET): $(sources) MANIFEST.MF
	-rm -rf obj
	mkdir -p obj
	javac -g -d obj $(sources) -cp asm-debug-all-4.0_RC1.jar
	jar cfm "$@" MANIFEST.MF asm-debug-all-4.0_RC1.jar -C obj/ .

scraper.exe : scraper.cs
	mcs -debug scraper.cs

# test API levels

test-8: docs-2.2_r01-linux 8.xml annotations/8.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-8/android.jar --out=_8.xml --docpath=docs-2.2_r01-linux/reference --annotations=annotations/8.xml || exit 1
	mono-xmltool --prettyprint _8.xml > _8_.xml || exit 1
	xmlstarlet c14n _8_.xml > _8.xml || exit 1
	rm _8_.xml

docs-2.2_r01-linux:
	wget http://dl-ssl.google.com/android/repository/docs-2.2_r01-linux.zip || exit 1
	unzip docs-2.2_r01-linux.zip || exit 1

8.xml:
	wget --output-document=8.xml "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/8.xml;hb=HEAD" || exit 1
	mono-xmltool --prettyprint 8.xml > 8_.xml || exit 1
	xmlstarlet c14n 8_.xml > 8.xml || exit 1
	rm 8_.xml

# (These rules are to get diff between 10 and 13.)
test-13: 10.xml annotations/13.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-13/android.jar --out=_13.xml --docpath=$(ANDROID_SDK_PATH)/docs/reference --annotations=annotations/13.xml || exit 1
	mono-xmltool --prettyprint _13.xml > _13_.xml || exit 1
	xmlstarlet c14n _13_.xml > _13.xml || exit 1
	rm _13_.xml

10.xml:
	wget --output-document=10.xml "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/10.xml;hb=HEAD" || exit 1
	mono-xmltool --prettyprint 10.xml > 10_.xml || exit 1
	xmlstarlet c14n 10_.xml > 10.xml || exit 1
	rm 10_.xml

# annotations

annotations/8.xml: scraper.exe tmpout/8-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/8-deprecated-members.xml docs-2.2_r01-linux/reference/ > annotations/8.xml

tmpout/8-deprecated-members.xml : scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-2.2_r01-linux/reference > tmpout/8-deprecated-members.xml || exit 1

annotations/13.xml: scraper.exe tmpout/13-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/13-deprecated-members.xml > annotations/13.xml

tmpout/13-deprecated-members.xml : scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh > tmpout/13-deprecated-members.xml || exit 1
