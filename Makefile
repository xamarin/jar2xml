API_LEVELS = 4 7 8 10 12 13 14 15 16 17

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
	javac -g -d obj $(sources) -cp asm-debug-all-4.0.jar
	mkdir -p tmp-asm-expanded
	unzip asm-debug-all-4.0.jar -d tmp-asm-expanded
	jar cfm "$@" MANIFEST.MF LICENSE-ASM.txt -C obj/ . -C tmp-asm-expanded .
	rm -r tmp-asm-expanded

scraper.exe : scraper.cs
	mcs -debug scraper.cs

$(API_LEVELS:%=api-%.xml.in): api-%.xml.in: Makefile jar2xml.jar docs-api-% annotations/%.xml
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/platforms/android-$*/android.jar --out=$@.tmp --droiddocpath=docs-api-$*/reference --annotations=annotations/$*.xml || exit 1
	mono-xmltool --prettyprint $@.tmp > $@.tmp2 || exit 1
	xmlstarlet c14n $@.tmp2 > $@ || exit 1
	rm $@.tmp $@.tmp2

# test API levels
test-4: api-4.xml.org api-4.xml.in

test-7: api-7.xml.org api-7.xml.in

test-8: api-8.xml.org api-8.xml.in

test-9: api-9.xml.org api-9.xml.in

test-10: api-10.xml.org api-10.xml.in

# (These rules are to get diff agains 10.)
test-12: api-10.xml.org api-12.xml.in

test-13: api-10.xml.org api-13.xml.in

clean-8:
	rm api-8.xml.in annotations/8.xml tmpout/8-deprecated-members.xml

clean-9:
	rm api-9.xml.in annotations/9.xml tmpout/9-deprecated-members.xml

clean-10:
	rm api-10.xml.in annotations/10.xml tmpout/10-deprecated-members.xml

clean-12:
	rm api-12.xml.in annotations/12.xml tmpout/12-deprecated-members.xml

clean-13:
	rm api-13.xml.in annotations/13.xml tmpout/13-deprecated-members.xml

clean-14:
	rm api-14.xml.in annotations/14.xml tmpout/14-deprecated-members.xml

clean-15:
	rm api-15.xml.in annotations/15.xml tmpout/15-deprecated-members.xml

clean-16:
	rm api-16.xml.in annotations/16.xml tmpout/16-deprecated-members.xml

clean-17:
	rm api-17.xml.in annotations/17.xml tmpout/17-deprecated-members.xml

# download and setup docs directory for each API profile

define extract-docs
	unzip $1 || exit 1
	mv $2 $@
endef

docs-%.zip:
	curl http://dl-ssl.google.com/android/repository/$@ > $@ || exit 1

# we couldn't find doc archive for Lv.4, so reusing Lv.7 archive here...
docs-api-4: docs-2.1_r01-linux.zip
	$(call extract-docs,$<,docs-2.1_r01-linux)

docs-api-7: docs-2.1_r01-linux.zip
	$(call extract-docs,$<,docs-2.1_r01-linux)

docs-api-8: docs-2.2_r01-linux.zip
	$(call extract-docs,$<,docs_r01-linux)

docs-api-9: docs-2.3_r01-linux.zip
	$(call extract-docs,$<,docs-2.3_r01-linux)

# API level 10 is Android v2.3.3; it's API level 9 (Android v2.3) with a few
# bugfixes which don't impact the documentation.
docs-api-10: docs-2.3_r01-linux.zip
	$(call extract-docs,$<,docs-2.3_r01-linux)

docs-api-12: docs-3.1_r01-linux.zip
	$(call extract-docs,$<,docs-3.1_r01-linux)

docs-api-13: docs-3.2_r01-linux.zip
	$(call extract-docs,$<,docs_r01-linux)

docs-api-14: docs-14_r01.zip
	$(call extract-docs,$<,docs)

docs-api-15: docs-15_r01.zip
	$(call extract-docs,$<,docs)

docs-api-16: docs-16_r01.zip
	$(call extract-docs,$<,docs)

docs-api-17: docs-17_r01.zip
	$(call extract-docs,$<,docs)

api-%.xml.org:
	# FIXME: disable them until some AOSP web repository becomes available.
	#curl "http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=api/$(patsubst api-%.xml.org,%.xml,$@);hb=HEAD" > $@ || exit 1
	#mono-xmltool --prettyprint $@ > $@.tmp || exit 1
	#xmlstarlet c14n $@.tmp > $@ || exit 1
	#rm $@.tmp

# annotations

$(API_LEVELS:%=annotations/%.xml): annotations/%.xml: Makefile scraper.exe docs-api-% tmpout/%-deprecated-members.xml
	mkdir -p annotations
	mono --debug scraper.exe tmpout/$*-deprecated-members.xml docs-api-$*/reference/ > annotations/$*.xml

$(API_LEVELS:%=tmpout/%-deprecated-members.xml): tmpout/%-deprecated-members.xml: Makefile docs-api-% scraper-main.sh scraper-collector.sh
	mkdir -p tmpout
	bash scraper-main.sh docs-api-$*/reference > tmpout/$*-deprecated-members.xml || exit 1

