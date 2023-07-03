package net.sf.okapi.filters.javascript;

import net.sf.okapi.filters.antlr.JavaScriptLexer;
import net.sf.okapi.filters.antlr.JavaScriptParser;
import net.sf.okapi.filters.antlr.JavaScriptParserBaseVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

public class JavaScriptVisitor {
    private final JavaScriptHandler handler;
    public JavaScriptVisitor(JavaScriptHandler handler) {
        this.handler = handler;
    }

    public void visit(Reader reader) throws IOException {
        CharStream stream = CharStreams.fromReader(reader);
        JavaScriptLexer lexer = new JavaScriptLexer(stream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        ParseTree tree = parser.program();
        Visitor visitor = new Visitor(stream, handler);
        handler.handleStart();
        visitor.visit(tree);
        handler.handleEnd();
    }
    private static class Visitor extends JavaScriptParserBaseVisitor<Void> {
        private int pos = 0;
        private final CharStream input;
        private final JavaScriptHandler handler;
        Visitor(CharStream stream, JavaScriptHandler handler) {
            this.input = stream;
            this.handler = handler;
        }
        @Override
        public Void visitObjectLiteralExpression(JavaScriptParser.ObjectLiteralExpressionContext ctx) {
            if (pos < ctx.getStart().getStartIndex()) {
                String text = input.getText(Interval.of(pos, ctx.getStart().getStartIndex() - 1));
                handler.handleSeparator(text);
            }
            handler.handleObjectStart();
            pos = ctx.getStart().getStartIndex() + 1;
            List<JavaScriptParser.PropertyAssignmentContext> contextList = ctx.objectLiteral().getRuleContexts(JavaScriptParser.PropertyAssignmentContext.class);
            for (JavaScriptParser.PropertyAssignmentContext context : contextList) {
                visit(context);
            }
            handler.handleObjectEnd();
            pos = ctx.getStop().getStopIndex()+1;
            return null;
        }

        @Override
        public Void visitPropertyName(JavaScriptParser.PropertyNameContext ctx) {
            // key
            if (pos < ctx.getStart().getStartIndex()) {
                String text = input.getText(Interval.of(pos, ctx.getStart().getStartIndex() - 1));
                handler.handleSeparator(text);
            }
            String text = ctx.getText();
            if (text.startsWith("'")) {
                handler.handleKey(text.substring(1, text.length() -1), JavaScriptValueTypes.SINGLE_QUOTED_STRING, JavaScriptKeyTypes.VALUE);
            } else if (text.startsWith("\"")) {
                handler.handleKey(text.substring(1, text.length() -1), JavaScriptValueTypes.DOUBLE_QUOTED_STRING, JavaScriptKeyTypes.VALUE);
            } else {
                handler.handleKey(text, JavaScriptValueTypes.DEFAULT, JavaScriptKeyTypes.VALUE);
            }

            pos = ctx.getStop().getStopIndex()+1;
            return null;
        }

        @Override
        public Void visitLiteralExpression(JavaScriptParser.LiteralExpressionContext ctx) {
            // value
            if (pos < ctx.getStart().getStartIndex()) {
                String text = input.getText(Interval.of(pos, ctx.getStart().getStartIndex() - 1));
                handler.handleSeparator(text);
            }
            String text = ctx.getText();
            if (text.startsWith("'")) {
                handler.handleValue(text.substring(1, text.length() -1), JavaScriptValueTypes.SINGLE_QUOTED_STRING);
            } else if (text.startsWith("\"")) {
                handler.handleValue(text.substring(1, text.length() -1), JavaScriptValueTypes.DOUBLE_QUOTED_STRING);
            } else {
                handler.handleValue(text, JavaScriptValueTypes.DEFAULT);
            }
            pos = ctx.getStop().getStopIndex()+1;
            return null;
        }

        @Override
        public Void visitPropertyExpressionAssignment(JavaScriptParser.PropertyExpressionAssignmentContext ctx) {
            if (pos < ctx.getStart().getStartIndex()) {
                String text = input.getText(Interval.of(pos, ctx.getStart().getStartIndex() - 1));
                handler.handleSeparator(text);
            }
            visit(ctx.propertyName());
            visit(ctx.singleExpression());
            pos = ctx.getStop().getStopIndex()+1;
            return null;
        }

        @Override
        public Void visitTerminal(TerminalNode node) {
            String text = input.getText(Interval.of(pos, node.getSymbol().getStopIndex()));
//            System.out.println(text);
            handler.handleSeparator(text);
            pos = node.getSymbol().getStopIndex()+1;
            return super.visitTerminal(node);
        }
    }
}
