API_LEVELS = 8 10 13

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

$(API_LEVELS:%=api-%.xml.in): api-%.xml.in: Makefile jar2xml.jar docs-api-% annotations/%.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-$*/android.jar --out=$@.tmp --docpath=docs-api-$*/reference --annotations=annotations/$*.xml || exit 1
	mono-xmltool --prettyprint $@.tmp > $@.tmp2 || exit 1
	xmlstarlet c14n $@.tmp2 > $@ || exit 1
	rm $@.tmp $@.tmp2

# test API levels
test-8: api-8.xml.org api-8.xml.in

test-9: api-9.xml.org api-9.xml.in

api-9.xml.in: docs-api-9 annotations/9.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-9/android.jar --out=api-9.xml.tmp --docpath=docs-api-9/reference --annotations=annotations/9.xml || exit 1
	mono-xmltool --prettyprint api-9.xml.tmp > api-9.xml.tmp2 || exit 1
	xmlstarlet c14n api-9.xml.tmp2 > api-9.xml.in || exit 1
	rm api-9.xml.tmp api-9.xml.tmp2

test-10: api-10.xml.org api-10.xml.in

# (This rule is to get diff between 10 and 13.)
test-13: api-10.xml.org api-13.xml.in

clean-test-8:
	rm api-8.xml.in annotations/8.xml tmpout/8-deprecated-members.xml

clean-test-9:
	rm api-9.xml.in annotations/9.xml tmpout/9-deprecated-members.xml

clean-test-10:
	rm api-10.xml.in annotations/10.xml tmpout/10-deprecated-members.xml

clean-test-13:
	rm api-13.xml.in annotations/13.xml tmpout/13-deprecated-members.xml

# download and setup docs directory for each API profile

define extract-docs
	unzip $1 || exit 1
	mv $2 $@
endef

docs-%.zip:
	curl http://dl-ssl.google.com/android/repository/$@ > $@ || exit 1

docs-api-8: docs-2.2_r01-linux.zip
	$(call extract-docs,$<,docs_r01-linux)

docs-api-9: docs-2.3_r01-linux.zip
	$(call extract-docs,$<,docs-2.3_r01-linux)

# API level 10 is Android v2.3.3; it's API level 9 (Android v2.3) with a few
# bugfixes which don't impact the documentation.
docs-api-10: docs-2.3_r01-linux.zip
	$(call extract-docs,$<,docs-2.3_r01-linux)

docs-api-13: docs-3.2_r01-linux.zip
	$(call extract-docs,$<,docs_r01-linux)

api-%.xml.org:
	curl "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/$(patsubst api-%.xml.org,%.xml,$@);hb=HEAD" > $@ || exit 1
	mono-xmltool --prettyprint $@ > $@.tmp || exit 1
	xmlstarlet c14n $@.tmp > $@ || exit 1
	rm $@.tmp

# annotations

$(API_LEVELS:%=annotations/%.xml): annotations/%.xml: Makefile scraper.exe docs-api-% tmpout/%-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/$*-deprecated-members.xml docs-api-$*/reference/ > annotations/$*.xml

$(API_LEVELS:%=tmpout/%-deprecated-members.xml): tmpout/%-deprecated-members.xml: Makefile docs-api-% scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-$*/reference > tmpout/$*-deprecated-members.xml || exit 1

annotations/9.xml: scraper.exe docs-api-9 tmpout/9-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/9-deprecated-members.xml docs-api-9/reference/ > annotations/9.xml

tmpout/9-deprecated-members.xml : docs-api-9 scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-9/reference > tmpout/9-deprecated-members.xml || exit 1

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
