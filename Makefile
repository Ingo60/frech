#
#   Makefile for "frech"
#
FREGEC = fregec.jar
PROGUARD = ~/bin/proguard.jar
SHRINK = ../shrink/bin/shrink-1.0.0-standalone.jar
ENTRY = frech.Daten
MAINCLASS = bin/frech/Daten.class

all: /home/ingo/bin/frech.jar

/home/ingo/bin/frech.jar: frech.slim.jar
	cp frech.slim.jar ~/bin/frech.jar

frech.slim.jar: frech.jar
	java8 -cp $(PROGUARD):$(SHRINK) de.contexo.Shrink frech.jar

frech.jar: $(MAINCLASS)
	cp $(FREGEC) frech.jar
	jar -uvf frech.jar -C bin frech
	jar -uvfe frech.jar $(ENTRY)

$(MAINCLASS):  src/frech/Daten.fr src/frech/FEN.fr src/frech/MDB.java
	mkdir -p bin
	rm -rf bin/frech
	java8 -jar $(FREGEC) -d bin -sp src/ -O -make $(ENTRY)
