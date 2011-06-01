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

