#
#   Makefile for "frech"
#
FREGEC = fregec.jar
PROGUARD = ~/bin/proguard.jar
SHRINK = ../shrink/bin/shrink-1.0.0-standalone.jar
ENTRY = frech.Protocol
MAINCLASS = bin/frech/Protocol.class
JAVA=java8

all: /home/ingo/bin/frech.jar

/home/ingo/bin/frech.jar: frech.slim.jar
	cp frech.slim.jar ~/bin/frech.jar

frech.slim.jar: frech.jar
	$(JAVA) -cp $(PROGUARD):$(SHRINK) de.contexo.Shrink frech.jar

frech.jar: $(MAINCLASS)
	cp $(FREGEC) frech.jar
	jar -uvf frech.jar -C bin frech
	jar -uvfe frech.jar $(ENTRY)

$(MAINCLASS):  src/frech/Protocol.fr src/frech/Daten.fr src/frech/FEN.fr src/frech/MDB.java
	mkdir -p bin
	rm -rf bin/frech
	$(JAVA) -jar $(FREGEC) -d bin -sp src/ -O -make $(ENTRY)
