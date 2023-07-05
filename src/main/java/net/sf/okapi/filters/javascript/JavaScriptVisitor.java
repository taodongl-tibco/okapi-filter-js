package net.sf.okapi.filters.javascript;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.io.Reader;

public class JavaScriptVisitor {
    private final JavaScriptHandler handler;
    private int objectIndex = 0;
    private boolean ExpectValue = false;
    public JavaScriptVisitor(JavaScriptHandler handler) {
        this.handler = handler;
    }

    public void visit(Reader reader) throws IOException {
        CharStream stream = CharStreams.fromReader(reader);
        JavaScriptLexer lexer = new JavaScriptLexer(stream);
        Token token = lexer.nextToken();
        handler.handleStart();
        while (token.getType() != JavaScriptLexer.EOF) {
            if (token.getType() == JavaScriptLexer.SEPARATOR) {
                handler.handleSeparator(token.getText());
            } else if (token.getType() == JavaScriptLexer.WHITE_SPACE) {
                handler.handleWhitespace(token.getText());
            } else if (token.getType() == JavaScriptLexer.OBJECT_START) {
                objectIndex++;
                ExpectValue = false;
                handler.handleObjectStart();
            } else if (token.getType() == JavaScriptLexer.OBJECT_END) {
                objectIndex--;
                ExpectValue = false;
                handler.handleObjectEnd();
            } else {
                String text = token.getText();
                if (objectIndex > 0) {
                    if (ExpectValue) {
                        if (text.startsWith("'")) {
                            handler.handleValue(text.substring(1, text.length() -1).replace("\\'", "'").replace("\"", "\\\""), JavaScriptValueTypes.SINGLE_QUOTED_STRING);
                        } else if (text.startsWith("\"")) {
                            handler.handleValue(text.substring(1, text.length() -1), JavaScriptValueTypes.DOUBLE_QUOTED_STRING);
                        } else {
                            handler.handleValue(text, JavaScriptValueTypes.DEFAULT);
                        }
                        ExpectValue = false;
                    } else {
                        if (text.startsWith("'")) {
                            handler.handleKey(text.substring(1, text.length() -1), JavaScriptValueTypes.SINGLE_QUOTED_STRING, JavaScriptKeyTypes.VALUE);
                        } else if (text.startsWith("\"")) {
                            handler.handleKey(text.substring(1, text.length() -1), JavaScriptValueTypes.DOUBLE_QUOTED_STRING, JavaScriptKeyTypes.VALUE);
                        } else {
                            handler.handleKey(text, JavaScriptValueTypes.DEFAULT, JavaScriptKeyTypes.VALUE);
                        }
                        ExpectValue = true;
                    }
                } else {
                    handler.handleWhitespace(text);
                }
            }
            token = lexer.nextToken();
        }
        handler.handleEnd();
    }

}
