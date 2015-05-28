API_LEVELS = 10 15 16 17 18 19 20 21 22 MNC

TARGET=jar2xml.jar 

all: $(TARGET) 

all-api: api-10.xml.in api-15.xml.in api-16.xml.in api-17.xml.in api-18.xml.in api-19.xml.in api-20.xml.in api-21.xml.in api-22.xml.in api-MNC.xml.in

clean:
	-rm -rf obj
	-rm $(TARGET)
	-rm -rf tmpout
	-rm -rf annotations
	-rm -rf scraper.exe scraper.exe.mdb

install:

uninstall:

deploy:

undeploy:

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
	javac -source 1.5 -target 1.6 -g -d obj $(sources) -cp asm-debug-all-5.0.3.jar
	mkdir -p tmp-asm-expanded
	unzip asm-debug-all-5.0.3.jar -d tmp-asm-expanded
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
test-10: api-10.xml.org api-10.xml.in

clean-10:
	rm api-10.xml.in annotations/10.xml tmpout/10-deprecated-members.xml

clean-15:
	rm api-15.xml.in annotations/15.xml tmpout/15-deprecated-members.xml

clean-16:
	rm api-16.xml.in annotations/16.xml tmpout/16-deprecated-members.xml

clean-17:
	rm api-17.xml.in annotations/17.xml tmpout/17-deprecated-members.xml

clean-18:
	rm api-18.xml.in annotations/18.xml tmpout/18-deprecated-members.xml

clean-19:
	rm api-19.xml.in annotations/19.xml tmpout/19-deprecated-members.xml

clean-20:
	rm api-20.xml.in annotations/20.xml tmpout/20-deprecated-members.xml

clean-21:
	rm api-21.xml.in annotations/21.xml tmpout/21-deprecated-members.xml

clean-22:
	rm api-22.xml.in annotations/22.xml tmpout/22-deprecated-members.xml

clean-MNC:
	rm api-MNC.xml.in annotations/MNC.xml tmpout/MNC-deprecated-members.xml

# download and setup docs directory for each API profile

define extract-docs
	unzip $1 || exit 1
	mv $2 $@
endef

docs-%.zip:
	curl http://dl-ssl.google.com/android/repository/$@ > $@ || exit 1

# API level 10 is Android v2.3.3; it's API level 9 (Android v2.3) with a few
# bugfixes which don't impact the documentation.
docs-api-10: docs-2.3_r01-linux.zip
	$(call extract-docs,$<,docs-2.3_r01-linux)

docs-api-15: docs-15_r02.zip
	$(call extract-docs,$<,docs)

docs-api-16: docs-16_r03.zip
	$(call extract-docs,$<,docs)

docs-api-17: docs-17_r02.zip
	$(call extract-docs,$<,docs)

docs-api-18: docs-18_r01.zip
	$(call extract-docs,$<,docs)

docs-api-19: docs-19_r01.zip
	$(call extract-docs,$<,docs)

# no 20 doc in final release either...
docs-api-20: docs-21_r01.zip
	$(call extract-docs,$<,docs)

docs-api-21: docs-21_r01.zip
	$(call extract-docs,$<,docs)

docs-api-22: docs-22_r01.zip
	$(call extract-docs,$<,docs)

docs-api-MNC: docs-MNC_r01.zip
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

