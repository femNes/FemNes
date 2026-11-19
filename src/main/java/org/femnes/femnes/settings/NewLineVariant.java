package org.femnes.femnes.settings;

import javafx.util.StringConverter;

public enum NewLineVariant {
    CR,
    LF,
    CRLF;

    public static final StringConverter<NewLineVariant> stringConverter = new StringConverter<>() {
        @Override
        public String toString(NewLineVariant object) {
            return object.toString();
        }
        @Override
        public NewLineVariant fromString(String string) {
            return NewLineVariant.valueOf(string.toUpperCase().strip());
        }
    };
}
