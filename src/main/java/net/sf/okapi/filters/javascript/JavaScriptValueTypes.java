package net.sf.okapi.filters.javascript;

public enum JavaScriptValueTypes {
    SINGLE_QUOTED_STRING("'"),
    DOUBLE_QUOTED_STRING("\""),
    SYMBOL(""),
    NUMBER(""),
    BOOLEAN(""),
    NULL(""),
    DEFAULT("");

    private final String quoteChar;

    private JavaScriptValueTypes(String quoteChar)
    {
        this.quoteChar = quoteChar;
    }

    public String getQuoteChar()
    {
        return this.quoteChar;
    }
}