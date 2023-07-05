package net.sf.okapi.filters.javascript;

import net.sf.okapi.common.encoder.EncoderContext;
import net.sf.okapi.common.encoder.EncoderManager;
import net.sf.okapi.common.filters.EventBuilder;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.filters.InlineCodeFinder;
import net.sf.okapi.common.resource.Code;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.TextFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaScriptEventBuilder extends EventBuilder {
    private final static Logger logger = LoggerFactory.getLogger(JavaScriptEventBuilder.class);
    private InlineCodeFinder codeFinder;
    private boolean escapeForwardSlashes = true;
    private EncoderManager encoderManager;
    public JavaScriptEventBuilder(String rootId, IFilter subFilter) {
        super(rootId, subFilter);
        codeFinder = null;
        // Get the output options
        if (subFilter.getParameters() != null ) {
            escapeForwardSlashes = subFilter.getParameters().getBoolean("escapeForwardSlashes");
        }
        this.encoderManager = subFilter.getEncoderManager();
    }
    @Override
    protected ITextUnit postProcessTextUnit(ITextUnit textUnit) {
        TextFragment text = textUnit.getSource().getFirstContent();
        String unescaped = unescape(text);
        text.setCodedText(unescaped);

        if (codeFinder != null) {
            encoderManager.updateEncoder(textUnit.getMimeType());
            codeFinder.process(text);
            // Pre-emptively re-encode anything we parsed out, since it won't happen otherwise
            for (Code code : text.getCodes()) {
                code.setData(encoderManager.encode(code.getData(), EncoderContext.TEXT));
                String codeDisplayText = code.getDisplayText();
                if (codeDisplayText != null) {
                    code.setDisplayText(encoderManager.encode(codeDisplayText, EncoderContext.TEXT));
                }
            }
        }
        return textUnit;
    }

    public static String decode(String value) {
        return unescape(new TextFragment(value));
    }

    private static String unescape(TextFragment text) {
        StringBuilder unescaped = new StringBuilder();
        char ch;
        for (int i = 0; i < text.length(); i++) {
            ch = text.charAt(i);
            switch(ch) {
                case '\\':
                    break;
                default:
                    unescaped.append(ch);
                    continue;
            }

            // previous char was '\'
            ch = text.charAt(++i);
            switch (ch) {
                case 'b':
                    unescaped.append('\b');
                    break;
                case 'f':
                    unescaped.append('\f');
                    break;
                case 'n':
                    unescaped.append('\n');
                    break;
                case 'r':
                    unescaped.append('\r');
                    break;
                case 't':
                    unescaped.append('\t');
                    break;
                case '\\':
                case '"':
                case '/':
                    unescaped.append(ch);
                    break;
                default: // Unexpected escape sequence
                    logger.warn("Unexpected JavaScript escape sequence '\\{}'.", ch);
                    unescaped.append('\\');
                    unescaped.append(ch);
                    break;
            }
        }

        return unescaped.toString();
    }

    public void setCodeFinder(InlineCodeFinder codeFinder) {
        this.codeFinder = codeFinder;
    }
}
