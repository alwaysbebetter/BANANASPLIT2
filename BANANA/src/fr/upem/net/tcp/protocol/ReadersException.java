package fr.upem.net.tcp.protocol;

import java.io.IOException;

public class ReadersException extends IOException {

    private static final long serialVersionUID = -1810727803680020453L;

    public ReadersException() {
        super();
    }

    public ReadersException(String s) {
        super(s);
    }

    public static void ensure(boolean b, String string) throws ReadersException {
        if (!b)
            throw new ReadersException(string);

    }
}