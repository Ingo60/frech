#
#   Makefile for "frech"
#
FREGEC = fregec.jar
PROGUARD = ~/bin/proguard.jar
SHRINK = ../shrink/bin/shrink-1.0.0-standalone.jar
ENTRY = frech.Data

all: /home/ingo/bin/frech.jar

/home/ingo/bin/frech.jar: frech.slim.jar
	cp frech.slim.jar ~/bin/frech.jar

frech.slim.jar: frech.jar
	java8 -cp $(PROGUARD):$(SHRINK) de.contexo.Shrink frech.jar

frech.jar: build/frech/Data.class
	cp $(FREGEC) frech.jar
	jar -uvf frech.jar -C build frech
	jar -uvfe frech.jar $(ENTRY)

build/frech/Data.class: src/frech/Data.fr
	mkdir -p build
	java -jar $(FREGEC) -d build -sp src/ -O -make $(ENTRY)
