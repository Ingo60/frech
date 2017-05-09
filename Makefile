#
#   Makefile for "frech"
#
FREGEC = fregec.jar
PROGUARD = ~/bin/proguard.jar
SHRINK = ../shrink/bin/shrink-1.0.0-standalone.jar
ENTRY = frech.Data
MAINCLASS = build/frech/Data.class

all: /home/ingo/bin/frech.jar

/home/ingo/bin/frech.jar: frech.slim.jar
	cp frech.slim.jar ~/bin/frech.jar

frech.slim.jar: frech.jar
	java8 -cp $(PROGUARD):$(SHRINK) de.contexo.Shrink frech.jar

frech.jar: $(MAINCLASS)
	cp $(FREGEC) frech.jar
	jar -uvf frech.jar -C build frech
	jar -uvfe frech.jar $(ENTRY)

$(MAINCLASS):  src/frech/Data.fr src/frech/FEN.fr src/frech/MDB.java
	mkdir -p build
	rm -rf build/frech
	java -jar $(FREGEC) -d build -sp src/ -O -make $(ENTRY)
