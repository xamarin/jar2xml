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

test-8: api-8.xml.org api-8.xml.in

api-8.xml.in: docs-api-8 annotations/8.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-8/android.jar --out=api-8.xml.tmp --docpath=docs-api-8/reference --annotations=annotations/8.xml || exit 1
	mono-xmltool --prettyprint api-8.xml.tmp > api-8.xml.tmp2 || exit 1
	xmlstarlet c14n api-8.xml.tmp2 > api-8.xml.in || exit 1
	rm api-8.xml.tmp api-8.xml.tmp2

test-9: api-9.xml.org api-9.xml.in

api-9.xml.in: docs-api-9 annotations/9.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-9/android.jar --out=api-9.xml.tmp --docpath=docs-api-9/reference --annotations=annotations/9.xml || exit 1
	mono-xmltool --prettyprint api-9.xml.tmp > api-9.xml.tmp2 || exit 1
	xmlstarlet c14n api-9.xml.tmp2 > api-9.xml.in || exit 1
	rm api-9.xml.tmp api-9.xml.tmp2

test-10: api-10.xml.org api-10.xml.in

api-10.xml.in: docs-api-10 annotations/10.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-10/android.jar --out=api-10.xml.tmp --docpath=docs-api-10/reference --annotations=annotations/10.xml || exit 1
	mono-xmltool --prettyprint api-10.xml.tmp > api-10.xml.tmp2 || exit 1
	xmlstarlet c14n api-10.xml.tmp2 > api-10.xml.in || exit 1
	rm api-10.xml.tmp api-10.xml.tmp2

# (This rule is to get diff between 10 and 13.)
test-13: api-10.xml.org api-13.xml.in

api-13.xml.in: docs-api-13 annotations/13.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-13/android.jar --out=api-13.xml.tmp --docpath=$(ANDROID_SDK_PATH)/docs/reference --annotations=annotations/13.xml || exit 1
	mono-xmltool --prettyprint api-13.xml.tmp > api-13.xml.tmp2 || exit 1
	xmlstarlet c14n api-13.xml.tmp2 > api-13.xml.in || exit 1
	rm api-13.xml.tmp api-13.xml.tmp2

clean-test-8:
	rm api-8.xml.in annotations/8.xml tmpout/8-deprecated-members.xml

clean-test-9:
	rm api-9.xml.in annotations/9.xml tmpout/9-deprecated-members.xml

clean-test-10:
	rm api-10.xml.in annotations/10.xml tmpout/10-deprecated-members.xml

clean-test-13:
	rm api-13.xml.in annotations/13.xml tmpout/13-deprecated-members.xml

# download and setup docs directory for each API profile

docs-api-8:
#	$(call get-docs docs-2.2_r01-linux.zip docs-2.2_r01-linux 8)
	wget http://dl-ssl.google.com/android/repository/docs-2.2_r01-linux.zip || exit 1
	unzip docs-2.2_r01-linux.zip || exit 1
	mv docs-2.2_r01-linux docs-api-8

docs-api-9:
#	$(call get-docs docs-2.3_r01-linux.zip docs-2.3_r01-linux 9)
	wget http://dl-ssl.google.com/android/repository/docs-2.3_r01-linux.zip || exit 1
	unzip docs-2.3_r01-linux.zip || exit 1
	mv docs-2.3_r01-linux docs-api-9

#There should be doc archive for API Level 10, but I cannot find it!
docs-api-10:
	ln -s docs-api-9 docs-api-10 || exit 1

docs-api-13:
#	$(call get-docs docs-3.2_r01-linux.zip docs_r01-linux 13)
	wget http://dl-ssl.google.com/android/repository/docs-3.2_r01-linux.zip || exit 1
	unzip docs-3.2_r01-linux.zip || exit 1
	mv docs_r01-linux docs-api-13

api-%.xml.org:
	wget --output-document=$@ "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/$(patsubst api-%.xml.org,%.xml,$@);hb=HEAD" || exit 1
	mono-xmltool --prettyprint $@ > $@.tmp || exit 1
	xmlstarlet c14n $@.tmp > $@ || exit 1
	rm $@.tmp

# annotations

annotations/8.xml: scraper.exe docs-api-8 tmpout/8-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/8-deprecated-members.xml docs-api-8/reference/ > annotations/8.xml

tmpout/8-deprecated-members.xml : docs-api-8 scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-8/reference > tmpout/8-deprecated-members.xml || exit 1

annotations/9.xml: scraper.exe docs-api-9 tmpout/9-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/9-deprecated-members.xml docs-api-9/reference/ > annotations/9.xml

tmpout/9-deprecated-members.xml : docs-api-9 scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-9/reference > tmpout/9-deprecated-members.xml || exit 1

annotations/10.xml: scraper.exe docs-api-10 tmpout/10-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/10-deprecated-members.xml docs-api-10/reference/ > annotations/10.xml

tmpout/10-deprecated-members.xml : docs-api-10 scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-10/reference > tmpout/10-deprecated-members.xml || exit 1

annotations/13.xml: scraper.exe tmpout/13-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/13-deprecated-members.xml > annotations/13.xml

tmpout/13-deprecated-members.xml : scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh > tmpout/13-deprecated-members.xml || exit 1

# generalized solution

api_profiles = 4 7 8 10 12 13

# this part seems working fine
tmpout/annot.%.xml: scraper.exe tmpout/annot.%.xml.tmp
	mkdir -p tmpout
	mono --debug scraper.exe $@.tmp docs-api-$(patsubst tmpout/annot.%.xml,%,$@)/reference/ > $@ || exit 1

tmpout/annot.%.xml.tmp : docs-api-% scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-$(patsubst tmpout/annot.%.xml.tmp,%,$@)/reference > $@ || exit 1

# FIXME: these don't work yet

define get-docs
	wget http://dl-ssl.google.com/android/repository/$1 || exit 1
	unzip $1 || exit 1
	mv $2 docs-api-$3
endef

build-8: api-8.xml

build-9: api-9.xml

build-10: api-10.xml

build-12: api-12.xml

build-13: api-13.xml

api-%.xml: docs-api-% api-%.xml.org tmpout/annot.%.xml
	$(call run-build $(patsubst api-%.xml,%,$@))

define run-build
	@echo running build for API Level $1
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-$1/android.jar --out=api-$1.xml --docpath=docs-api-$1/reference --annotations=tmpout/annot.$1.xml || exit 1
	mono-xmltool --prettyprint api-$1.xml > api-$1.xml.tmp || exit 1
	xmlstarlet c14n api-$1.xml.tmp > api-$1.xml || exit 1
	rm api-$1.xml.tmp
endef
