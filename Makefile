
all:

install:
	install -d -m755  $(DESTDIR)/bin/
	install -m755 wrapper $(DESTDIR)/bin/wrapper